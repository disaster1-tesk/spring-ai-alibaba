package com.alibaba.cloud.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StateGraphTest {
    @Test
    public void graphTest() throws Exception {
        StateGraph workflow = new StateGraph()
                .addEdge(START, "agent_1")
                .addNode("agent_1", node_async(state -> {
                    System.out.print("agent_1");
                    System.out.println(state);
                    return Map.of("prop1", "test");
                }))
                .addEdge("agent_1", END);

        CompiledGraph app = workflow.compile();

        Optional result = app.invoke(Map.of("input", "test1"));
        assertTrue(result.isPresent());

        Map<String, String> expected = Map.of("input", "test1", "prop1", "test");

    }
}
