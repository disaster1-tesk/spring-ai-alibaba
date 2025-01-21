package com.alibaba.cloud.ai.graph.state.reduce;

import com.alibaba.cloud.ai.graph.state.Reducer;

import java.lang.reflect.Array;
import java.util.stream.Stream;

public class AppendArrayReducer<T> implements Reducer<T[]> {

	@Override
	public T[] apply(T[] oldValue, T[] newValue) {
		if (newValue == null) {
			return oldValue;
		}
		return Stream.concat(Stream.of(oldValue), Stream.of(newValue))
			.toArray(size -> (T[]) Array.newInstance(oldValue.getClass().getComponentType(), size));
	}

}
