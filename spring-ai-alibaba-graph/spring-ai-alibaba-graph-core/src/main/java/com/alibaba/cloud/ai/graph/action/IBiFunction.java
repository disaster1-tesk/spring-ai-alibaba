package com.alibaba.cloud.ai.graph.action;

import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface IBiFunction<T,R> {

    CompletableFuture<R> apply(T t, RunnableConfig config) throws Exception;
}
