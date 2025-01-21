package com.alibaba.cloud.ai.graph.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMapperSingleton {

	private static class SingletonHelper {

		private static final ObjectMapper INSTANCE = new ObjectMapper();

	}

	private ObjectMapperSingleton() {
	}

	public static ObjectMapper getInstance() {
		return SingletonHelper.INSTANCE;
	}

}
