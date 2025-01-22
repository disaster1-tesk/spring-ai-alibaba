package com.alibaba.cloud.ai.graph.action;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.state.OverAllState;
import com.alibaba.cloud.ai.graph.NodeActionDescriptor;

import java.util.Map;

@FunctionalInterface
public interface NodeAction {

	Map<String,Object> apply(OverAllState t) throws Exception;

	default NodeActionDescriptor getNodeActionDescriptor(){
		return NodeActionDescriptor.EMPTY;
	}

}
