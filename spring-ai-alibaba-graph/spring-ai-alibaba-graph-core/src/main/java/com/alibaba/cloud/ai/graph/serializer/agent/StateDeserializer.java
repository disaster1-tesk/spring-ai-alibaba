package com.alibaba.cloud.ai.graph.serializer.agent;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.state.OverAllState;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class StateDeserializer extends JsonDeserializer<OverAllState> {

	@Override
	public OverAllState deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
		JsonNode node = parser.getCodec().readTree(parser);

		Map<String, Object> data = new HashMap<>();

		var dataNode = node.has("data") ? node.get("data") : node;
		if (dataNode.has(OverAllState.INPUT) && StringUtils.hasText(dataNode.get(OverAllState.INPUT).asText())) {
			data.put(OverAllState.INPUT, dataNode.get(OverAllState.INPUT).asText());
		}
		if (dataNode.has(OverAllState.OUTPUT)) {
			JsonNode outputNode = dataNode.get(OverAllState.OUTPUT);
			if (StringUtils.hasText(outputNode.asText())) {
				data.put(OverAllState.OUTPUT, outputNode.asText());
			}
			else {
				if (!outputNode.isNull()) {
					var agentOutcome = ctx.readValue(outputNode.traverse(parser.getCodec()), AgentOutcome.class);
					data.put("agent_outcome", agentOutcome);
				}
			}
		}
		if (dataNode.has(OverAllState.SUB_GRAPH)) {
			JsonNode outputNode = dataNode.get(OverAllState.SUB_GRAPH);
			var agentOutcome = ctx.readValue(outputNode.traverse(parser.getCodec()),
					CompiledGraph.AsyncNodeGenerator.class);
			data.put(OverAllState.SUB_GRAPH, agentOutcome);
		}

		return new OverAllState(data);
	}

}
