package com.alibaba.cloud.ai.graph.state;

import com.alibaba.cloud.ai.graph.state.reduce.AppendArrayReducer;
import com.alibaba.cloud.ai.graph.state.reduce.AppendListReducer;
import com.alibaba.cloud.ai.graph.state.reduce.ImmutableReducer;
import com.alibaba.cloud.ai.graph.state.reduce.OverrideReducer;

public enum ReduceStrategyType {

	IMMUTABLE(new ImmutableReducer()),

	OVERRIDE(new OverrideReducer()),

	APPEND_LIST(new AppendListReducer<>()),

	APPEND_ARRAY(new AppendArrayReducer<>());

	private Reducer<?> reducer;

	public Reducer<?> reducer() {
		return reducer;
	}

	ReduceStrategyType(Reducer<?> reducer) {
		this.reducer = reducer;
	}

}
