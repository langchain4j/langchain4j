package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;

class DefaultZhipuAiHelper {

    public static List<Embedding> toEmbed(ZhipuAiEmbeddingResponse response) {
        return response.getData().stream()
                .map(zhipuAiEmbedding -> Embedding.from(zhipuAiEmbedding.getEmbedding()))
                .collect(Collectors.toList());
    }

    public static String toEmbedTexts(List<TextSegment> textSegments) {
        List<String> embedText = textSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());
        if (Utils.isNullOrEmpty(embedText)) {
            return null;
        }
        return embedText.get(0);
    }

    public static List<ZhipuAiChatCompletionTool> toTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(DefaultZhipuAiHelper::toTool)
                .collect(Collectors.toList());
    }

    private static ZhipuAiChatCompletionTool toTool(ToolSpecification toolSpecification) {
        return ZhipuAiChatCompletionTool.builder()
                .type("function")
                .function(toFunction(toolSpecification))
                .build();
    }

    private static ZhipuAiChatCompletionFunction toFunction(ToolSpecification toolSpecification) {
        return ZhipuAiChatCompletionFunction.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toFunctionParameters(toolSpecification.parameters()))
                .build();
    }

    private static ZhipuAiChatCompletionFunctionParameters toFunctionParameters(ToolParameters toolParameters) {
        return ZhipuAiChatCompletionFunctionParameters.builder()
                .type(toolParameters.type())
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }


    public static List<ZhipuAiChatCompletionMessage> toZhipuAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultZhipuAiHelper::toZhipuAiMessage)
                .collect(Collectors.toList());
    }

    private static ZhipuAiChatCompletionMessage toZhipuAiMessage(ChatMessage message) {

        if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            return ZhipuAiChatCompletionMessage.builder()
                    .role(ZhipuAiChatCompletionRole.SYSTEM)
                    .content(systemMessage.text())
                    .build();
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return ZhipuAiChatCompletionMessage.builder()
                    .role(ZhipuAiChatCompletionRole.USER)
                    .content(userMessage.text())
                    .build();
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (!aiMessage.hasToolExecutionRequests()) {
                return ZhipuAiChatCompletionMessage.builder()
                        .role(ZhipuAiChatCompletionRole.ASSISTANT)
                        .content(aiMessage.text())
                        .build();
            }
            List<ZhipuAiChatCompletionToolCall> toolCallArrayList = new ArrayList<>();
            for (ToolExecutionRequest executionRequest : aiMessage.toolExecutionRequests()) {
                toolCallArrayList.add(ZhipuAiChatCompletionToolCall.builder()
                        .function(
                                ZhipuAiChatCompletionFunctionCall.builder()
                                        .name(executionRequest.name())
                                        .arguments(executionRequest.arguments())
                                        .build()
                        )
                        .type("function")
                        .id(executionRequest.id())
                        .build()
                );
            }
            return ZhipuAiChatCompletionMessage.builder()
                    .role(ZhipuAiChatCompletionRole.ASSISTANT)
                    .content(aiMessage.text())
                    .toolCalls(toolCallArrayList)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage resultMessage = (ToolExecutionResultMessage) message;
            return ZhipuAiChatCompletionMessage.builder()
                    .role(ZhipuAiChatCompletionRole.TOOL)
                    .content(resultMessage.text())
                    .build();
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    public static AiMessage aiMessageFrom(ZhipuAiChatCompletionResponse response) {
        ZhipuAiChatCompletionMessage message = response.getChoices().get(0).getMessage();
        if (isNullOrEmpty(message.getToolCalls())) {
            return AiMessage.from(message.getContent());
        }

        return AiMessage.from(specificationsFrom(message.getToolCalls()));
    }

    public static List<ToolExecutionRequest> specificationsFrom(List<ZhipuAiChatCompletionToolCall> toolCalls) {
        List<ToolExecutionRequest> specifications = new ArrayList<>(toolCalls.size());
        for (ZhipuAiChatCompletionToolCall toolCall : toolCalls) {
            specifications.add(
                    ToolExecutionRequest.builder()
                            .id(toolCall.getId())
                            .name(toolCall.getFunction().getName())
                            .arguments(toolCall.getFunction().getArguments())
                            .build()
            );
        }
        return specifications;
    }


    public static TokenUsage tokenUsageFrom(ZhipuAiUsage zhipuUsage) {
        if (zhipuUsage == null) {
            return null;
        }
        return new TokenUsage(
                zhipuUsage.getPromptTokens(),
                zhipuUsage.getCompletionTokens(),
                zhipuUsage.getTotalTokens()
        );
    }

    public static FinishReason finishReasonFrom(String finishReason) {
        if (finishReason == null) {
            return null;
        }
        switch (finishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            default:
                return OTHER;
        }
    }
}
