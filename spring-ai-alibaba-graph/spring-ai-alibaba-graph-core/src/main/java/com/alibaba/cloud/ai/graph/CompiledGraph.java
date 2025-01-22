package com.alibaba.cloud.ai.graph;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.state.OverAllState;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static java.lang.String.format;

/**
 * Represents a compiled graph of nodes and edges. This class manage the StateGraph
 */
@Slf4j
public class CompiledGraph {

    public enum StreamMode {

        VALUES, SNAPSHOTS

    }

    @Getter
    final StateGraph stateGraph;

    @Getter
    final Map<String, AsyncNodeActionWithConfig> nodes = new LinkedHashMap<>();

    @Getter
    final Map<String, EdgeValue> edges = new LinkedHashMap<>();

    public static int maxIterations = 25;

    @Getter
    private final CompileConfig compileConfig;

    /**
     * Constructs a CompiledGraph with the given StateGraph.
     *
     * @param stateGraph the StateGraph to be used in this CompiledGraph
     */
    protected CompiledGraph(StateGraph stateGraph, CompileConfig compileConfig) {
        this.stateGraph = stateGraph;
        this.compileConfig = compileConfig;
        stateGraph.nodes.forEach(n -> nodes.put(n.id(), n.action()));

        stateGraph.edges.forEach(e -> edges.put(e.sourceId(), e.target()));
    }

    /**
     * Same of {@link #stateOf(RunnableConfig)} but throws an IllegalStateException if
     * checkpoint is not found.
     *
     * @param config the RunnableConfig
     * @return the StateSnapshot of the given RunnableConfig
     * @throws IllegalStateException if the saver is not defined, or no checkpoint is
     *                               found
     */
    public StateSnapshot getState(RunnableConfig config) {
        return stateOf(config).orElseThrow(() -> (new IllegalStateException("Missing Checkpoint!")));
    }

    /**
     * Get the StateSnapshot of the given RunnableConfig.
     *
     * @param config the RunnableConfig
     * @return an Optional of StateSnapshot of the given RunnableConfig
     * @throws IllegalStateException if the saver is not defined
     */
    public Optional<StateSnapshot> stateOf(RunnableConfig config) {
        BaseCheckpointSaver saver = compileConfig.checkpointSaver()
                .orElseThrow(() -> (new IllegalStateException("Missing CheckpointSaver!")));

        return saver.get(config).map(checkpoint -> StateSnapshot.of(checkpoint, config));

    }

    /**
     * Update the state of the graph with the given values. If asNode is given, it will be
     * used to determine the next node to run. If not given, the next node will be
     * determined by the state graph.
     *
     * @param config the RunnableConfig containg the graph state
     * @param values the values to be updated
     * @param asNode the node id to be used for the next node. can be null
     * @return the updated RunnableConfig
     * @throws Exception when something goes wrong
     */
    public RunnableConfig updateState(RunnableConfig config, Map<String, Object> values, String asNode)
            throws Exception {
        BaseCheckpointSaver saver = compileConfig.checkpointSaver()
                .orElseThrow(() -> (new IllegalStateException("Missing CheckpointSaver!")));

        // merge values with checkpoint values
        Checkpoint branchCheckpoint = saver.get(config)
                .map(Checkpoint::new)
                .map(cp -> cp.updateState(values))
                .orElseThrow(() -> (new IllegalStateException("Missing Checkpoint!")));

        String nextNodeId = null;
        if (asNode != null) {
            nextNodeId = nextNodeId(asNode, branchCheckpoint.getState());
        }
        // update checkpoint in saver
        RunnableConfig newConfig = saver.put(config, branchCheckpoint);

        return RunnableConfig.builder(newConfig).checkPointId(branchCheckpoint.getId()).nextNode(nextNodeId).build();
    }

    /***
     * Update the state of the graph with the given values.
     * @param config the RunnableConfig containg the graph state
     * @param values the values to be updated
     * @return the updated RunnableConfig
     * @throws Exception when something goes wrong
     */
    public RunnableConfig updateState(RunnableConfig config, Map<String, Object> values) throws Exception {
        return updateState(config, values, null);
    }

    public EdgeValue getEntryPoint() {
        return stateGraph.getEntryPoint();
    }

    /**
     * Sets the maximum number of iterations for the graph execution.
     *
     * @param maxIterations the maximum number of iterations
     * @throws IllegalArgumentException if maxIterations is less than or equal to 0
     */
    public void setMaxIterations(int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be > 0!");
        }
        this.maxIterations = maxIterations;
    }

    private String nextNodeId(EdgeValue route, Map<String, Object> state, String nodeId) throws Exception {

        if (route == null) {
            throw StateGraph.RunnableErrors.missingEdge.exception(nodeId);
        }
        if (route.id() != null) {
            return route.id();
        }
        if (route.value() != null) {
            OverAllState derefState = new OverAllState(state);
            AsyncEdgeAction condition = route.value().action();
            String newRoute = condition.apply(derefState).get();
            String result = route.value().mappings().get(newRoute);
            if (result == null) {
                throw StateGraph.RunnableErrors.missingNodeInEdgeMapping.exception(nodeId, newRoute);
            }
            return result;
        }
        throw StateGraph.RunnableErrors.executionError
                .exception(format("invalid edge value for nodeId: [%s] !", nodeId));
    }

    /**
     * Determines the next node ID based on the current node ID and state.
     *
     * @param nodeId the current node ID
     * @param state  the current state
     * @return the next node ID
     * @throws Exception if there is an error determining the next node ID
     */
    public String nextNodeId(String nodeId, Map<String, Object> state) throws Exception {
        return nextNodeId(edges.get(nodeId), state, nodeId);

    }

    public String getEntryPoint(Map<String, Object> state) throws Exception {
        return nextNodeId(stateGraph.getEntryPoint(), state, "entryPoint");
    }

    public boolean shouldInterruptBefore(@NonNull String nodeId, String previousNodeId) {
        if (previousNodeId == null) { // FIX RESUME ERROR
            return false;
        }
        return Arrays.asList(compileConfig.getInterruptBefore()).contains(nodeId);
    }

    public boolean shouldInterruptAfter(String nodeId, String previousNodeId) {
        if (nodeId == null) { // FIX RESUME ERROR
            return false;
        }
        return Arrays.asList(compileConfig.getInterruptAfter()).contains(nodeId);
    }

    @Deprecated
    public Optional<Checkpoint> addCheckpoint(RunnableConfig config, String nodeId, Map<String, Object> state,
                                              String nextNodeId) throws Exception {
        if (compileConfig.checkpointSaver().isPresent()) {
            Checkpoint cp = Checkpoint.builder().nodeId(nodeId).state(state).nextNodeId(nextNodeId).build();
            compileConfig.checkpointSaver().get().put(config, cp);
            return Optional.of(cp);
        }
        return Optional.empty();

    }



    Map<String, Object> getInitialStateFromSchema() {
        return new HashMap<>();
    }

    public Map<String, Object> getInitialState(Map<String, Object> inputs, RunnableConfig config) {

        return compileConfig.checkpointSaver()
                .flatMap(saver -> saver.get(config))
                .map(cp -> OverAllState.updateState(cp.getState(), inputs))
                .orElseGet(() -> OverAllState.updateState(getInitialStateFromSchema(), inputs));
    }

    OverAllState useCurrentState(Map<String, Object> data) throws ClassNotFoundException {
        return stateGraph.getStateSerializer().stateOf(data);
    }

    OverAllState cloneState(Map<String, Object> data) throws IOException, ClassNotFoundException {
        return stateGraph.getStateSerializer().cloneObject(data);
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     * @throws Exception if there is an error creating the stream
     */
    public AsyncGenerator<NodeOutput> stream(Map<String, Object> inputs, RunnableConfig config) throws Exception {
        Objects.requireNonNull(config, "config cannot be null");

        return new AsyncNodeGenerator(inputs, config);
    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @return an AsyncGenerator stream of NodeOutput
     * @throws Exception if there is an error creating the stream
     */
    public AsyncGenerator<NodeOutput> stream(Map<String, Object> inputs) throws Exception {
        return this.stream(inputs, RunnableConfig.builder().build());
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an Optional containing the final state if present, otherwise an empty
     * Optional
     * @throws Exception if there is an error during invocation
     */
    public Optional invoke(Map<String, Object> inputs, RunnableConfig config) throws Exception {

        Iterator<NodeOutput> sourceIterator = stream(inputs, config).iterator();

        java.util.stream.Stream<NodeOutput> result = StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(sourceIterator, Spliterator.ORDERED), false);

        return  result.reduce((a, b) -> b).map(NodeOutput::state);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @return an Optional containing the final state if present, otherwise an empty
     * Optional
     * @throws Exception if there is an error during invocation
     */
    public Optional invoke(Map<String, Object> inputs) throws Exception {
        return this.invoke(inputs, RunnableConfig.builder().build());
    }

    /**
     * Generates a drawable graph representation of the state graph.
     *
     * @param type                  the type of graph representation to generate
     * @param title                 the title of the graph
     * @param printConditionalEdges whether to print conditional edges
     * @return a diagram code of the state graph
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type, String title, boolean printConditionalEdges) {

        String content = type.generator.generate(this.stateGraph, title, printConditionalEdges);

        return new GraphRepresentation(type, content);
    }

    /**
     * Generates a drawable graph representation of the state graph.
     *
     * @param type  the type of graph representation to generate
     * @param title the title of the graph
     * @return a diagram code of the state graph
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type, String title) {

        String content = type.generator.generate(this.stateGraph, title, true);

        return new GraphRepresentation(type, content);
    }

    /**
     * Generates a drawable graph representation of the state graph with default title.
     *
     * @param type the type of graph representation to generate
     * @return a diagram code of the state graph
     */
    public GraphRepresentation getGraph(GraphRepresentation.Type type) {
        return getGraph(type, "Graph Diagram", true);
    }

    public class AsyncNodeGenerator implements AsyncGenerator<NodeOutput> {

        Map<String, Object> currentState;

        OverAllState overAllState;

        @Getter
        String currentNodeId;

        String nextNodeId;

        int iteration = 0;

        RunnableConfig config;

        protected AsyncNodeGenerator(Map<String, Object> inputs, RunnableConfig config) {
            final boolean isResumeRequest = (inputs == null);

            if (isResumeRequest) {

                log.trace("RESUME REQUEST");

                BaseCheckpointSaver saver = compileConfig.checkpointSaver()
                        .orElseThrow(() -> (new IllegalStateException(
                                "inputs cannot be null (ie. resume request) if no checkpoint saver is configured")));
                Checkpoint startCheckpoint = saver.get(config)
                        .orElseThrow(() -> (new IllegalStateException("Resume request without a saved checkpoint!")));

                this.currentState = startCheckpoint.getState();

                // Reset checkpoint id
                this.config = config.withCheckPointId(null);
                this.overAllState = new OverAllState(currentState);

                this.nextNodeId = startCheckpoint.getNextNodeId();
                this.currentNodeId = null;
                log.trace("RESUME FROM {}", startCheckpoint.getNodeId());
            } else {

                log.trace("START");

                Map<String, Object> initState = getInitialState(inputs, config);
                // patch for backward support of AppendableValue
                this.currentState = initState;
                this.nextNodeId = null;
                this.currentNodeId = START;
                this.overAllState = new OverAllState(new HashMap<>());
                this.overAllState.nodeStateAdd(START, initState);
                this.config = config;
            }
        }

        public NodeOutput buildNodeOutput(String nodeId) throws Exception {
            return NodeOutput.of(nodeId, cloneState(currentState));
        }

        public NodeOutput buildCurrentNodeOutput(String nodeId) throws Exception {
            return NodeOutput.of(nodeId, useCurrentState(currentState));
        }

        protected NodeOutput buildStateSnapshot(Checkpoint checkpoint) {
            return StateSnapshot.of(checkpoint, config);
        }

        @Override
        public Data<NodeOutput> next() {
            // GUARD: CHECK MAX ITERATION REACHED
            if (++iteration > maxIterations) {
                log.warn("Maximum number of iterations ({}) reached!", maxIterations);
                return Data.done();
            }

            // GUARD: CHECK IF IT IS END
            if (nextNodeId == null && currentNodeId == null)
                return Data.done();

            CompletableFuture<NodeOutput> future = new CompletableFuture<>();

            try {

                if (START.equals(currentNodeId)) {
                    nextNodeId = getEntryPoint(currentState);

                    currentNodeId = nextNodeId;
                    addCheckpoint(config, START, currentState, nextNodeId);
                    return Data.of(NodeOutput.of(START, overAllState));
                }

                if (StateGraph.END.equals(nextNodeId)) {
                    nextNodeId = null;
                    String nodeId = currentNodeId;
                    currentNodeId = null;
                    if (currentState.containsKey(OverAllState.SUB_GRAPH)) {
                        return Data.of(buildCurrentNodeOutput(StateGraph.END));
                    } else {
                        return Data.of(NodeOutput.of(END, overAllState.nodeState(nodeId)));
                    }
                }

                // check on previous node
                if (shouldInterruptAfter(currentNodeId, nextNodeId))
                    return Data.done();

                if (shouldInterruptBefore(nextNodeId, currentNodeId))
                    return Data.done();

                currentNodeId = nextNodeId;

                AsyncNodeActionWithConfig action = nodes.get(currentNodeId);

                if (action == null)
                    throw StateGraph.RunnableErrors.missingNode.exception(currentNodeId);

                future = action.apply(overAllState, config).thenApply(partialState -> {
                    try {
                        currentState = partialState;
                        nextNodeId = nextNodeId(currentNodeId, currentState);
                        overAllState.nodeStateAdd(currentNodeId, partialState);
                        if (currentState.containsKey(OverAllState.SUB_GRAPH)) {
                            return buildCurrentNodeOutput(currentNodeId);
                        } else {
                            Optional<Checkpoint> cp = addCheckpoint(config, currentNodeId, currentState, nextNodeId);
                            return (cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS)
                                    ? buildStateSnapshot(cp.get()) : NodeOutput.of(currentNodeId, overAllState);
                        }

                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                future.completeExceptionally(e);
            }
            return Data.of(future);

        }

    }

    /**
     * Creates an AsyncGenerator stream of NodeOutput based on the provided inputs.
     *
     * @param inputs the input map
     * @param config the invoke configuration
     * @return an AsyncGenerator stream of NodeOutput
     */
    public AsyncGenerator<NodeOutput> streamSnapshots(Map<String, Object> inputs, RunnableConfig config) {
        Objects.requireNonNull(config, "config cannot be null");

        return new AsyncNodeGenerator(inputs, config.withStreamMode(StreamMode.SNAPSHOTS));
    }

}
