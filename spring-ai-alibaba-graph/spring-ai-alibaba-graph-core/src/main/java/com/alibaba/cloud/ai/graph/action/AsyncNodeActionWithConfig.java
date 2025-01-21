package com.alibaba.cloud.ai.graph.action;


import com.alibaba.cloud.ai.graph.NodeActionDescriptor;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import java.util.concurrent.CompletableFuture;

public interface AsyncNodeActionWithConfig<T, R>
        extends IBiFunction<T,R> {

    /**
     * Applies this action to the given agent state.
     *
     * @param t the agent state
     * @return a CompletableFuture representing the result of the action
     */
    CompletableFuture<R> apply(T t, RunnableConfig config) throws Exception;


    default NodeActionDescriptor getNodeActionDescriptor(){
        return null;
    };
}
