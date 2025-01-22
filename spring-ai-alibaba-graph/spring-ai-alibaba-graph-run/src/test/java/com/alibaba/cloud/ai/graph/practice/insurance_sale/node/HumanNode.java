package com.alibaba.cloud.ai.graph.practice.insurance_sale.node;

import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.state.OverAllState;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.studio.StreamingServer.USER_INPUT;

public class HumanNode implements NodeAction {

	@Override
	public Map<String, Object> apply(OverAllState state) {
		USER_INPUT.clear();
		synchronized (USER_INPUT) {
			try {
				USER_INPUT.wait();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

		}
		USER_INPUT.remove(OverAllState.OUTPUT);
		return new HashMap<>(USER_INPUT);
	}

}
