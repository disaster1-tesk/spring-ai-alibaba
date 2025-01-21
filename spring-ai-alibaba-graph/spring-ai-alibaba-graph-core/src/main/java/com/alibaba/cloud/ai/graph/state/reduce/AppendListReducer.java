package com.alibaba.cloud.ai.graph.state.reduce;

import com.alibaba.cloud.ai.graph.state.Reducer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class AppendListReducer<T> implements Reducer<List<T>> {

	@Override
	public List<T> apply(List<T> oldValue, List<T> newValue) {
		if (newValue == null) {
			return oldValue;
		}
		try {
			oldValue.addAll(newValue);
			return oldValue;
		}
		catch (UnsupportedOperationException ex) {
			log.error(
					"Unsupported operation: probably because the appendable channel has been initialized with a immutable List. Check please !");
			throw ex;
		}
	}

}
