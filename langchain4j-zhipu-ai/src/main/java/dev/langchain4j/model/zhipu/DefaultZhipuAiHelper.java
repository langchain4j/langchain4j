package dev.langchain4j.model.zhipu;

import com.fasterxml.jackson.databind.node.TextNode;
import com.zhipu.oapi.service.v4.embedding.EmbeddingApiResponse;
import com.zhipu.oapi.service.v4.model.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

class DefaultZhipuAiHelper {

    static List<Embedding> toEmbed(List<EmbeddingApiResponse> response) {
        return response.stream()
                .map(zhipuAiEmbedding -> Embedding.from(zhipuAiEmbedding.getData().getData().get(0).getEmbedding().stream().map(Double::floatValue).collect(toList())))
                .collect(toList());
    }

    static List<ChatTool> toTools(List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(toolSpecification -> {
                    ChatTool chatTool = new ChatTool();
                    chatTool.setType(ChatToolType.FUNCTION.value());
                    chatTool.setFunction(toFunction(toolSpecification));
                    return chatTool;
                })
                .collect(toList());
    }

    private static ChatFunction toFunction(ToolSpecification toolSpecification) {
        return ChatFunction.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toFunctionParameters(toolSpecification.parameters()))
                .build();
    }

    private static ChatFunctionParameters toFunctionParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return new ChatFunctionParameters();
        }
        return new ChatFunctionParameters(
                toolParameters.type(),
                toolParameters.properties(),
                toolParameters.required()
        );
    }



    static List<com.zhipu.oapi.service.v4.model.ChatMessage> toZhipuAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultZhipuAiHelper::toZhipuAiMessage)
                .collect(toList());
    }

    private static com.zhipu.oapi.service.v4.model.ChatMessage toZhipuAiMessage(ChatMessage message) {

        if (message instanceof SystemMessage) {
            SystemMessage systemMessage = (SystemMessage) message;
            com.zhipu.oapi.service.v4.model.ChatMessage chatMessage = new com.zhipu.oapi.service.v4.model.ChatMessage();
            chatMessage.setRole("system");
            chatMessage.setContent(systemMessage.text());
            return chatMessage;
        }

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            com.zhipu.oapi.service.v4.model.ChatMessage chatMessage = new com.zhipu.oapi.service.v4.model.ChatMessage();
            chatMessage.setRole("user");
            chatMessage.setContent(userMessage.singleText());
            return chatMessage;
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (!aiMessage.hasToolExecutionRequests()) {
                com.zhipu.oapi.service.v4.model.ChatMessage chatMessage = new com.zhipu.oapi.service.v4.model.ChatMessage();
                chatMessage.setRole("assistant");
                chatMessage.setContent(aiMessage.text());
                return chatMessage;
            }
            List<ToolCalls> toolCallsArrayList = new ArrayList<>();
            for (ToolExecutionRequest executionRequest : aiMessage.toolExecutionRequests()) {
                ToolCalls function = new ToolCalls(
                        new ChatFunctionCall(
                                executionRequest.name(),
                                new TextNode(executionRequest.arguments())
                        ),
                        executionRequest.id(),
                        "function"
                );
                toolCallsArrayList.add(function);
            }
            com.zhipu.oapi.service.v4.model.ChatMessage chatMessage = new com.zhipu.oapi.service.v4.model.ChatMessage();
            chatMessage.setRole("assistant");
            chatMessage.setContent(aiMessage.text());
            chatMessage.setTool_calls(toolCallsArrayList);
            return chatMessage;
        }

        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage resultMessage = (ToolExecutionResultMessage) message;
            com.zhipu.oapi.service.v4.model.ChatMessage chatMessage = new com.zhipu.oapi.service.v4.model.ChatMessage();
            chatMessage.setRole("tool");
            chatMessage.setContent(resultMessage.text());
            return chatMessage;
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }

    static AiMessage aiMessageFrom(ModelData response) {
        com.zhipu.oapi.service.v4.model.ChatMessage message = response.getChoices().get(0).getMessage();
        if (isNullOrEmpty(message.getTool_calls())) {
            return AiMessage.from((String) message.getContent());
        }

        return AiMessage.from(specificationsFrom(message.getTool_calls()));
    }

    static List<ToolExecutionRequest> specificationsFrom(List<ToolCalls> toolCalls) {
        List<ToolExecutionRequest> specifications = new ArrayList<>(toolCalls.size());
        for (ToolCalls toolCall : toolCalls) {
            specifications.add(
                    ToolExecutionRequest.builder()
                            .id(toolCall.getId())
                            .name(toolCall.getFunction().getName())
                            .arguments(toolCall.getFunction().getArguments().toString())
                            .build()
            );
        }
        return specifications;
    }

    static Usage getEmbeddingUsage(List<EmbeddingApiResponse> responses) {
        Usage tokenUsage = new Usage();
        tokenUsage.setCompletionTokens(0);
        tokenUsage.setPromptTokens(0);
        tokenUsage.setTotalTokens(0);

        for (EmbeddingApiResponse response : responses) {
            tokenUsage.setPromptTokens(tokenUsage.getPromptTokens() + response.getData().getUsage().getPromptTokens());
            tokenUsage.setCompletionTokens(tokenUsage.getCompletionTokens() + response.getData().getUsage().getCompletionTokens());
            tokenUsage.setTotalTokens(tokenUsage.getTotalTokens() + response.getData().getUsage().getTotalTokens());
        }
        return tokenUsage;
    }


    static TokenUsage tokenUsageFrom(Usage zhipuUsage) {
        if (zhipuUsage == null) {
            return null;
        }
        return new TokenUsage(
                zhipuUsage.getPromptTokens(),
                zhipuUsage.getCompletionTokens(),
                zhipuUsage.getTotalTokens()
        );
    }

    static FinishReason finishReasonFrom(String finishReason) {
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
