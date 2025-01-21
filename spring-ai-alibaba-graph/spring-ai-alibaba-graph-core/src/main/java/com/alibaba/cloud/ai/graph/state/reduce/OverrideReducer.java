package com.alibaba.cloud.ai.graph.state.reduce;

import com.alibaba.cloud.ai.graph.state.Reducer;

public class OverrideReducer implements Reducer<Object> {

	@Override
	public Object apply(Object oldValue, Object newValue) {
		return newValue;
	}

}
