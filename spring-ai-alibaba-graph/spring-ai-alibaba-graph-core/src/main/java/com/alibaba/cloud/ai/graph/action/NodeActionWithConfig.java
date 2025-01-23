package com.alibaba.cloud.ai.graph.action;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.OverAllState;

import java.util.Map;

@FunctionalInterface
public interface NodeActionWithConfig {

	Map<String,Object> apply(OverAllState state, RunnableConfig config) throws Exception;

}
