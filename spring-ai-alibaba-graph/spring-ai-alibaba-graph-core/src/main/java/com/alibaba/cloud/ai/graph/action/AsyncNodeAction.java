package com.alibaba.cloud.ai.graph.action;


import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Represents an asynchronous node action that operates on an agent state and returns
 * state update.
 *
 */
@FunctionalInterface
public interface AsyncNodeAction<T,R> {

	/**
	 * Applies this action to the given agent state.
	 * @param t the agent state
	 * @return a CompletableFuture representing the result of the action
	 */
	CompletableFuture<R> apply(T t);

	/**
	 * Creates an asynchronous node action from a synchronous node action.
	 * @param syncAction the synchronous node action
	 * @return an asynchronous node action
	 */
	static <T,R> AsyncNodeAction<T,R> node_async(NodeAction<T,R> syncAction) {
		return t -> {
			CompletableFuture<R> result = new CompletableFuture<>();
			try {
				result.complete(syncAction.apply(t));
			}
			catch (Exception e) {
				result.completeExceptionally(e);
			}
			return result;
		};
	}

}
