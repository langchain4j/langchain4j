package dev.langchain4j.model.spark;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.output.TokenUsage;
import io.github.briqt.spark4j.constant.SparkMessageRole;
import io.github.briqt.spark4j.model.SparkMessage;
import io.github.briqt.spark4j.model.SparkSyncChatResponse;
import io.github.briqt.spark4j.model.request.function.SparkRequestFunctionMessage;
import io.github.briqt.spark4j.model.request.function.SparkRequestFunctionParameters;
import io.github.briqt.spark4j.model.request.function.SparkRequestFunctionProperty;
import io.github.briqt.spark4j.model.response.SparkResponseFunctionCall;
import io.github.briqt.spark4j.model.response.SparkResponseUsage;
import io.github.briqt.spark4j.model.response.SparkTextUsage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ren
 * @since 2024/3/15 15:35
 */
public class SparkUtils {
    public static TokenUsage toUsage(SparkTextUsage textUsage) {
        return new TokenUsage(
                textUsage.getPromptTokens(),
                textUsage.getCompletionTokens(),
                textUsage.getTotalTokens()
        );
    }

    public static AiMessage toAiMessage(SparkSyncChatResponse response) {
        String content = response.getContent();
        if (!Utils.isNullOrBlank(content)) {
            return new AiMessage(content);
        }
        SparkResponseFunctionCall functionCall = response.getFunctionCall();
        if (functionCall != null) {
            return new AiMessage(Collections.singletonList(
                    ToolExecutionRequest.builder()
                            .arguments(functionCall.getArguments())
                            .name(functionCall.getName())
                            .build()));
        }
        return new AiMessage("");
    }

    public static SparkRequestFunctionMessage toSparkFunction(ToolSpecification toolSpecification) {
        ToolParameters parameters = toolSpecification.parameters();
        return new SparkRequestFunctionMessage(
                toolSpecification.name(),
                toolSpecification.description(),
                toSparkFunctionParameters(parameters)
        );
    }

    public static SparkRequestFunctionParameters toSparkFunctionParameters(ToolParameters parameters) {
        Map<String, SparkRequestFunctionProperty> propertyMap = new HashMap<>();
        Map<String, Map<String, Object>> properties = parameters.properties();
        properties.forEach(
                (k, v) -> propertyMap.put(k, new SparkRequestFunctionProperty(
                        v.getOrDefault("type", "").toString(),
                        v.getOrDefault("description", "").toString()
                ))
        );
        return new SparkRequestFunctionParameters(parameters.type(), propertyMap, parameters.required());
    }

    public static List<SparkMessage> toSparkMessage(List<ChatMessage> message) {
        if (Utils.isNullOrEmpty(message)) {
            return new ArrayList<>();
        }
        return message.stream().map(SparkUtils::toSparkMessage).collect(Collectors.toList());
    }

    public static SparkMessage toSparkMessage(ChatMessage message) {
        return new SparkMessage(toSparkRole(message.type()), message.text());
    }

    private static String toSparkRole(ChatMessageType messageType) {
        switch (messageType) {
            case SYSTEM:
                return SparkMessageRole.SYSTEM;
            case USER:
                return SparkMessageRole.USER;
            case AI:
                return SparkMessageRole.ASSISTANT;
        }
        return SparkMessageRole.USER;
    }

    public static TokenUsage toUsage(SparkResponseUsage usage) {
        if (usage != null) {
            return new TokenUsage(
                    usage.getText().getPromptTokens(),
                    usage.getText().getCompletionTokens(),
                    usage.getText().getTotalTokens()
            );
        }
        return new TokenUsage();
    }
}
