package com.alibaba.cloud.ai.graph.action;

import com.alibaba.cloud.ai.graph.state.NodeState;
import com.alibaba.cloud.ai.graph.NodeActionDescriptor;

import java.util.Map;
public interface NodeAction<T,R> {

	R apply(T t) throws Exception;

	default NodeActionDescriptor getNodeActionDescriptor(){
		return NodeActionDescriptor.EMPTY;
	}

}
