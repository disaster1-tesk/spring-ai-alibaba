/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph;

import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.bsc.async.AsyncGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static java.util.Arrays.asList;

public class StateStreamGraphTest {
    @Test
    public void testNodeActionStream() throws Exception {
        StateGraph stateGraph = new StateGraph(() -> new OverAllState().registerKeyAndStrategy("messages", new AppendStrategy()).registerKeyAndStrategy("count", (oldValue, newValue) -> oldValue == null ? newValue : 1)).addNode("collectInput", node_async(s -> {
            // 处理输入
            String input = s.value("input", "");
            return Map.of("messages", "Received: " + input, "count", 1);
        })).addNode("processData", node_async(s -> {
            // 处理数据 - 这里可以是耗时操作，会以流式方式返回结果
            final List<String> data = asList("这是", "一个", "流式", "输出", "测试");
            AtomicInteger timeOff = new AtomicInteger(1);
            final AsyncGenerator<NodeOutput> it = AsyncGenerator.collect(data.iterator(), (index, add) -> add.accept(of("processData", index, 500L * timeOff.getAndIncrement(), s)));
            return Map.of("messages", it);
        })).addNode("generateResponse", node_async(s -> {
            // 生成最终响应
            int count = s.value("count", 0);
            return Map.of("messages", "Response generated (processed " + count + " items)", "result", "Success");
        })).addEdge(START, "collectInput").addEdge("collectInput", "processData").addEdge("processData", "generateResponse").addEdge("generateResponse", END);

        CompiledGraph compiledGraph = stateGraph.compile();
        // 初始化输入
        for (var output : compiledGraph.stream(Map.of("input", "hoho~~"))) {
            if (output instanceof AsyncGenerator<?>) {
                AsyncGenerator asyncGenerator = (AsyncGenerator) output;
                System.out.println("Streaming chunk: " + asyncGenerator);
            } else {
                System.out.println("Node output: " + output);
            }
        }
    }

    static CompletableFuture<NodeOutput> of(String node, String index, long delayInMills, OverAllState overAllState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayInMills);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return new StreamingOutput(index, node, overAllState);
        });
        // return completedFuture("e" + index);
    }


    @Test
    public void testConditionEdgeStream() throws Exception {
        StateGraph stateGraph = new StateGraph(() -> new OverAllState()
                .registerKeyAndStrategy("messages", new AppendStrategy()))
                .addNode("llmNode", AsyncNodeAction.node_async((t) -> Map.of()))
                .addNode("toolNode", AsyncNodeAction.node_async((t) -> Map.of()))
                .addNode("result", AsyncNodeAction.node_async((t) -> Map.of()))
                .addEdge(START, "llmNode")
                .addEdge("llmNode", "toolNode")
                .addEdge("toolNode", "result")
                .addEdge("result", END);


    }
}
