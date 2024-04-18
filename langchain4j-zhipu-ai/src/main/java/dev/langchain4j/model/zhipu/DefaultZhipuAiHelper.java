package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.zhipu.chat.*;
import dev.langchain4j.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.model.zhipu.shared.Usage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;

class DefaultZhipuAiHelper {

    public static List<Embedding> toEmbed(List<EmbeddingResponse> response) {
        return response.stream()
                .map(zhipuAiEmbedding -> Embedding.from(zhipuAiEmbedding.getEmbedding()))
                .collect(Collectors.toList());
    }

    public static List<Tool> toTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(toolSpecification -> Tool.from(toFunction(toolSpecification)))
                .collect(Collectors.toList());
    }

    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toFunctionParameters(toolSpecification.parameters()))
                .build();
    }

    private static Parameters toFunctionParameters(ToolParameters toolParameters) {
        return Parameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }


    public static List<Message> toZhipuAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultZhipuAiHelper::toZhipuAiMessage)
                .collect(Collectors.toList());
    }

    private static Message toZhipuAiMessage(ChatMessage message) {

        if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            return dev.langchain4j.model.zhipu.chat.SystemMessage.builder()
                    .content(systemMessage.text())
                    .build();
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return dev.langchain4j.model.zhipu.chat.UserMessage.builder()
                    .content(userMessage.singleText())
                    .build();
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (!aiMessage.hasToolExecutionRequests()) {
                return AssistantMessage.builder()
                        .content(aiMessage.text())
                        .build();
            }
            List<ToolCall> toolCallArrayList = new ArrayList<>();
            for (ToolExecutionRequest executionRequest : aiMessage.toolExecutionRequests()) {
                toolCallArrayList.add(ToolCall.builder()
                        .function(
                                FunctionCall.builder()
                                        .name(executionRequest.name())
                                        .arguments(executionRequest.arguments())
                                        .build()
                        )
                        .type(ToolType.FUNCTION)
                        .id(executionRequest.id())
                        .build()
                );
            }
            return AssistantMessage.builder()
                    .content(aiMessage.text())
                    .toolCalls(toolCallArrayList)
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage resultMessage = (ToolExecutionResultMessage) message;
            return ToolMessage.builder()
                    .content(resultMessage.text())
                    .build();
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    public static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        AssistantMessage message = response.getChoices().get(0).getMessage();
        if (isNullOrEmpty(message.getToolCalls())) {
            return AiMessage.from(message.getContent());
        }

        return AiMessage.from(specificationsFrom(message.getToolCalls()));
    }

    public static List<ToolExecutionRequest> specificationsFrom(List<ToolCall> toolCalls) {
        List<ToolExecutionRequest> specifications = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
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

    public static Usage getEmbeddingUsage(List<EmbeddingResponse> responses) {
        Usage tokenUsage = Usage.builder()
                .completionTokens(0)
                .promptTokens(0)
                .totalTokens(0)
                .build();

        for (EmbeddingResponse response : responses) {
            tokenUsage.add(response.getUsage());
        }
        return tokenUsage;
    }


    public static TokenUsage tokenUsageFrom(Usage zhipuUsage) {
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
