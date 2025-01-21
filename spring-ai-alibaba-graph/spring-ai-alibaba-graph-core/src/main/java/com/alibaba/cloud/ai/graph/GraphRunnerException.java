package com.alibaba.cloud.ai.graph;

/**
 * Exception thrown when there is an error during the execution of a graph runner.
 */
public class GraphRunnerException extends RuntimeException {

	/**
	 * Constructs a new GraphRunnerException with the specified error message.
	 *
	 * @param errorMessage the detail message
	 */
	public GraphRunnerException(String errorMessage) {
		super(errorMessage);
	}

	/**
	 * Instantiates a new Graph runner exception.
	 *
	 * @param message the message
	 * @param cause   the cause
	 */
	public GraphRunnerException(String message, Throwable cause) {
		super(message, cause);
	}
}