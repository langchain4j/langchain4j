package dev.langchain4j.model.cohere.internal.mapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.cohere.internal.api.*;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;

import static dev.langchain4j.internal.Utils.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class CohereMapper {

    public static String toCohereMessage(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage) {
            return ((UserMessage) chatMessage).singleText();
        } else if (chatMessage instanceof ToolExecutionResultMessage) {
            return "";
        }
        throw new IllegalArgumentException("Cohere Messages have to end with UserMessage or ToolExecutionResult");
    }

    public static List<ToolResult> toToolResults(List<ChatMessage> chatMessages) {
        List<ToolResult> toolResults = new ArrayList<>();
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            ChatMessage chatMessage = chatMessages.get(i);
            if (chatMessage instanceof ToolExecutionResultMessage) {
                ToolExecutionResultMessage toolExecutionResult = ((ToolExecutionResultMessage) chatMessage);
                toolResults.add(ToolResult.builder()
                        .call(ToolCall.builder().name(toolExecutionResult.toolName()).build())
                        .outputs(Collections.singletonList(Collections.singletonMap("Result", toolExecutionResult.text())))
                        .build());
            } else {
                break;
            }
        }
        return toolResults.isEmpty() ? null : toolResults;
    }

    public static String toPreamble(List<ChatMessage> chatMessages) {
        String preamble = chatMessages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> ((SystemMessage) message).text())
                .collect(joining("\n\n"));

        if (isNullOrBlank(preamble)) {
            return null;
        } else {
            return preamble;
        }
    }

    public static List<ChatHistory> toChatHistory(List<ChatMessage> chatMessages) {
        List<ChatHistory> history =
                chatMessages.stream().map(CohereMapper::toChatHistory).filter(Objects::nonNull).collect(toList());
        return history.isEmpty() ? null : history;
    }

    public static ChatHistory toChatHistory(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) chatMessage;
            return ChatHistory.builder().role(CohereRole.USER).message(userMessage.singleText()).build();
        } else if (chatMessage instanceof AiMessage) {
            AiMessage aimessage = (AiMessage) chatMessage;
            return ChatHistory.builder().role(CohereRole.CHATBOX).message(aimessage.toString()).build();
        }
        return null;
    }

    public static AiMessage toAiMessage(CohereChatResponse response) {

        if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {

            return AiMessage.from(
                    response.getToolCalls().stream().map(
                            toolCall -> ToolExecutionRequest.builder()
                                            .name(toolCall.getName())
                                            .arguments(Json.toJson(toolCall.getParameters()))
                                            .build()
                    ).collect(toList())
            );
        }

        return AiMessage.from(response.getText());
    }

    public static TokenUsage toTokenUsage(BilledUnits billedUnits) {
        if (billedUnits == null) {
            return null;
        }
        return new TokenUsage(billedUnits.getInputTokens(), billedUnits.getOutputTokens());
    }

    public static List<Tool> toCohereTools(List<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) {
            return null;
        }
        return toolSpecifications.stream().map(CohereMapper::toCohereTool).collect(toList());
    }

    public static Tool toCohereTool(ToolSpecification toolSpecification) {
        return Tool.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameterDefinitions(parseParameter(toolSpecification.parameters()))
                .build();
    }

    private static Map<String, ParameterDefinition> parseParameter(ToolParameters params) {
        if (params == null) {
            return null;
        }
        Map<String, ParameterDefinition> parameterDefinitions = new HashMap<>();
        Set<String> required = new HashSet<>(params.required());
        params.properties().forEach((k, map) -> {
            ParameterDefinition definition = ParameterDefinition.builder()
                    .type((String) map.getOrDefault("type", "str"))
                    .required(required.contains(k))
                    .description((String) map.getOrDefault("description", null))
                    .build();
            parameterDefinitions.put(k, definition);
        });
        return parameterDefinitions;
    }
}
