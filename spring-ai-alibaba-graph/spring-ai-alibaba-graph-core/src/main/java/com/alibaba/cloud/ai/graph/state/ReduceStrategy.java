package com.alibaba.cloud.ai.graph.state;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ReduceStrategy {

	ReduceStrategyType value() default ReduceStrategyType.APPEND_LIST;

}
