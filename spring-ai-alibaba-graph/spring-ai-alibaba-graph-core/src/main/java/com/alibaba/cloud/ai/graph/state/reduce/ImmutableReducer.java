package com.alibaba.cloud.ai.graph.state.reduce;

import com.alibaba.cloud.ai.graph.state.Reducer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImmutableReducer implements Reducer<Object> {

	@Override
	public Object apply(Object oldValue, Object newValue) {
		return oldValue;
	}

}
