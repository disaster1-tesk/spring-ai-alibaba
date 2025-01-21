package com.alibaba.cloud.ai.graph;

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.state.*;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.utils.ObjectMapperSingleton;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;

import java.io.IOException;
import java.lang.reflect.*;
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
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static java.lang.String.format;

/**
 * Represents a compiled graph of nodes and edges. This class manage the StateGraph
 */
@Slf4j
public class CompiledGraph {

    /**
     * The enum Stream mode.
     */
    public enum StreamMode {

        /**
         * Values stream mode.
         */
        VALUES,
        /**
         * Snapshots stream mode.
         */
        SNAPSHOTS

    }

    /**
     * The State graph.
     */
    @Getter
    final StateGraph stateGraph;

    /**
     * The Nodes.
     */
    @Getter
    final Map<String, AsyncNodeActionWithConfig> nodes = new LinkedHashMap<>();

    /**
     * The Edges.
     */
    @Getter
    final Map<String, EdgeValue> edges = new LinkedHashMap<>();

    /**
     * The constant maxIterations.
     */
    public static int maxIterations = 25;

    @Getter
    private final CompileConfig compileConfig;

    /**
     * Constructs a CompiledGraph with the given StateGraph.
     *
     * @param stateGraph    the StateGraph to be used in this CompiledGraph
     * @param compileConfig the compile config
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
     * @throws IllegalStateException if the saver is not defined, or no checkpoint is                               found
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

        return saver.get(config).map(checkpoint -> StateSnapshot.of(checkpoint, config, stateGraph.getStateFactory()));

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

    /**
     * Gets entry point.
     *
     * @return the entry point
     */
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
            NodeState derefState = stateGraph.getStateFactory().apply(state);
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

    /**
     * Gets entry point.
     *
     * @param state the state
     * @return the entry point
     * @throws Exception the exception
     */
    public String getEntryPoint(Map<String, Object> state) throws Exception {
        return nextNodeId(stateGraph.getEntryPoint(), state, "entryPoint");
    }

    /**
     * Should interrupt before boolean.
     *
     * @param nodeId         the node id
     * @param previousNodeId the previous node id
     * @return the boolean
     */
    public boolean shouldInterruptBefore(@NonNull String nodeId, String previousNodeId) {
        if (previousNodeId == null) { // FIX RESUME ERROR
            return false;
        }
        return Arrays.asList(compileConfig.getInterruptBefore()).contains(nodeId);
    }

    /**
     * Should interrupt after boolean.
     *
     * @param nodeId         the node id
     * @param previousNodeId the previous node id
     * @return the boolean
     */
    public boolean shouldInterruptAfter(String nodeId, String previousNodeId) {
        if (nodeId == null) { // FIX RESUME ERROR
            return false;
        }
        return Arrays.asList(compileConfig.getInterruptAfter()).contains(nodeId);
    }

    /**
     * Add checkpoint optional.
     *
     * @param config     the config
     * @param nodeId     the node id
     * @param state      the state
     * @param nextNodeId the next node id
     * @return the optional
     * @throws Exception the exception
     */
    public Optional<Checkpoint> addCheckpoint(RunnableConfig config, String nodeId, Map<String, Object> state,
                                              String nextNodeId) throws Exception {
        if (compileConfig.checkpointSaver().isPresent()) {
            Checkpoint cp = Checkpoint.builder().nodeId(nodeId).state(cloneState(state)).nextNodeId(nextNodeId).build();
            compileConfig.checkpointSaver().get().put(config, cp);
            return Optional.of(cp);
        }
        return Optional.empty();

    }

    /**
     * Gets initial state from schema.
     *
     * @return the initial state from schema
     */
    Map<String, Object> getInitialStateFromSchema() {
        return new HashMap<>();
    }

    /**
     * Gets initial state.
     *
     * @param inputs the inputs
     * @param config the config
     * @return the initial state
     */
    public Map<String, Object> getInitialState(Map<String, Object> inputs, RunnableConfig config) {

        return compileConfig.checkpointSaver()
                .flatMap(saver -> saver.get(config))
                .map(cp -> NodeState.updateState(cp.getState(), inputs))
                .orElseGet(() -> NodeState.updateState(getInitialStateFromSchema(), inputs));
    }

    /**
     * Use current state node state.
     *
     * @param data the data
     * @return the node state
     * @throws ClassNotFoundException the class not found exception
     */
    NodeState useCurrentState(Map<String, Object> data) throws ClassNotFoundException {
        return stateGraph.getStateSerializer().stateOf(data);
    }

    /**
     * Clone state node state.
     *
     * @param data the data
     * @return the node state
     * @throws IOException            the io exception
     * @throws ClassNotFoundException the class not found exception
     */
    NodeState cloneState(Map<String, Object> data) throws IOException, ClassNotFoundException {
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

        return new AsyncNodeGenerator<>(inputs, config);
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
     * @return an Optional containing the final state if present, otherwise an empty Optional
     * @throws Exception if there is an error during invocation
     */
    public Optional invoke(Map<String, Object> inputs, RunnableConfig config) throws Exception {

        Iterator<NodeOutput> sourceIterator = stream(inputs, config).iterator();

        java.util.stream.Stream<NodeOutput> result = StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(sourceIterator, Spliterator.ORDERED), false);

        return result.reduce((a, b) -> b).map(NodeOutput::state);
    }

    /**
     * Invokes the graph execution with the provided inputs and returns the final state.
     *
     * @param inputs the input map
     * @return an Optional containing the final state if present, otherwise an empty Optional
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

    /**
     * The type Async node generator.
     *
     * @param <Output> the type parameter
     */
    public class AsyncNodeGenerator<Output extends NodeOutput> implements AsyncGenerator<Output> {

        /**
         * The Current state.
         */
        Map<String, Object> currentState;

        /**
         * The Current node id.
         */
        @Getter
        String currentNodeId;

        /**
         * The Next node id.
         */
        String nextNodeId;

        /**
         * The Iteration.
         */
        int iteration = 0;

        /**
         * The Config.
         */
        RunnableConfig config;

        /**
         * Instantiates a new Async node generator.
         *
         * @param inputs the inputs
         * @param config the config
         */
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

                this.nextNodeId = startCheckpoint.getNextNodeId();
                this.currentNodeId = null;
                log.trace("RESUME FROM {}", startCheckpoint.getNodeId());
            } else {

                log.trace("START");

                Map<String, Object> initState = getInitialState(inputs, config);
                // patch for backward support of AppendableValue
                NodeState initializedState = stateGraph.getStateFactory().apply(initState);
                this.currentState = initializedState.data();
                this.nextNodeId = null;
                this.currentNodeId = StateGraph.START;
                this.config = config;
            }
        }

        /**
         * Build node output output.
         *
         * @param nodeId the node id
         * @return the output
         * @throws Exception the exception
         */
        public Output buildNodeOutput(String nodeId) throws Exception {
            return (Output) NodeOutput.of(nodeId, cloneState(currentState));
        }

        /**
         * Build current node output output.
         *
         * @param nodeId the node id
         * @return the output
         * @throws Exception the exception
         */
        public Output buildCurrentNodeOutput(String nodeId) throws Exception {
            return (Output) NodeOutput.of(nodeId, useCurrentState(currentState));
        }

        /**
         * Build state snapshot output.
         *
         * @param checkpoint the checkpoint
         * @return the output
         */
        protected Output buildStateSnapshot(Checkpoint checkpoint) {
            return (Output) StateSnapshot.of(checkpoint, config, stateGraph.getStateFactory());
        }

        @Override
        public Data<Output> next() {
            // GUARD: CHECK MAX ITERATION REACHED
            if (++iteration > maxIterations) {
                log.warn("Maximum number of iterations ({}) reached!", maxIterations);
                return Data.done();
            }

            // GUARD: CHECK IF IT IS END
            if (nextNodeId == null && currentNodeId == null)
                return Data.done();

            CompletableFuture<Output> future = new CompletableFuture<>();

            try {

                if (StateGraph.START.equals(currentNodeId)) {
                    nextNodeId = getEntryPoint(currentState);
                    currentNodeId = nextNodeId;
                    addCheckpoint(config, StateGraph.START, currentState, nextNodeId);
                    return Data.of(buildNodeOutput(StateGraph.START));
                }

                if (StateGraph.END.equals(nextNodeId)) {
                    nextNodeId = null;
                    currentNodeId = null;
                    if (currentState.containsKey(NodeState.SUB_GRAPH)) {
                        return Data.of(buildCurrentNodeOutput(StateGraph.END));
                    } else {
                        return Data.of(buildNodeOutput(StateGraph.END));
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

                future = action.apply(convertState(action, cloneState(currentState)), config).thenApply(partialState -> {
                    try {
                        currentState = NodeState.updateState(currentState, partialState);
                        nextNodeId = nextNodeId(currentNodeId, currentState);

                        if (currentState.containsKey(NodeState.SUB_GRAPH)) {
                            return buildCurrentNodeOutput(currentNodeId);
                        } else {
                            Optional<Checkpoint> cp = addCheckpoint(config, currentNodeId, currentState, nextNodeId);
                            return (cp.isPresent() && config.streamMode() == StreamMode.SNAPSHOTS)
                                    ? buildStateSnapshot(cp.get()) : buildNodeOutput(currentNodeId);
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
     * @param action
     * @param state
     * @param <I>
     * @return
     * @throws GraphStateException
     */
    private <I> I convertState(AsyncNodeActionWithConfig action, NodeState state)
            throws GraphStateException {
        for (Type genericInterface : action.getClass().getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType type) {
                Type[] actualTypeArguments = type.getActualTypeArguments();
                Type inputType = actualTypeArguments[0];
                Field[] fields = getClassFromType(inputType).getDeclaredFields();
                Map<String, Object> inputStateMap = new HashMap<>();
                populateVal(state, genericInterface, fields, inputStateMap);
                ObjectMapper objectMapper = ObjectMapperSingleton.getInstance();
                Type actualTypeArgument = actualTypeArguments[1];
                objectMapper.convertValue(inputStateMap, getClassFromType(actualTypeArgument));
            }
        }
        throw new GraphStateException("Can not find InputState of " + action.getClass().getName());

    }

    /**
     * @param type
     * @return
     */
    private static Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
            return null; // For other types of types, you can handle them as required
        }
    }

    /**
     * @param state
     * @param genericInterface
     * @param fields
     * @param inputStateMap
     */
    private static void populateVal(NodeState state, Type genericInterface, Field[] fields, Map<String, Object> inputStateMap) throws GraphStateException {
        for (Field field : fields) {
            Optional<Object> value = state.value(field.getName());
            if (field.isAnnotationPresent(ReduceStrategy.class)) {
                if (field.isAnnotationPresent(DefaultValue.class)) {
                    DefaultValue defaultValue = field.getAnnotation(DefaultValue.class);
                    try {
                        Supplier<?> supplier = defaultValue.value().getConstructor().newInstance();
                        inputStateMap.put(field.getName(), value.orElseGet(supplier));
                    } catch (Exception e) {
                        throw new GraphStateException("Can not construct supplier of "
                                + genericInterface.getTypeName() + "." + field.getName());
                    }
                } else {
                    inputStateMap.put(field.getName(), value.orElse(null));
                }
            }
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

        return new AsyncNodeGenerator<>(inputs, config.withStreamMode(StreamMode.SNAPSHOTS));
    }

}
