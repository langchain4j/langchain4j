package dev.langchain4j.model.qianfan;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingResponse;
import dev.langchain4j.model.qianfan.client.chat.Parameters;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.model.qianfan.client.chat.Message;
import dev.langchain4j.model.qianfan.client.chat.Role;
import dev.langchain4j.model.qianfan.client.chat.FunctionCall;
import dev.langchain4j.model.qianfan.client.chat.Function;
import dev.langchain4j.model.qianfan.client.completion.CompletionResponse;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

public class InternalQianfanHelper {

    public static List<Function> toFunctions(Collection<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .map(InternalQianfanHelper::toFunction)
                .collect(toList());
    }

    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toOpenAiParameters(toolSpecification.parameters()))
                .build();
    }

    private static Parameters toOpenAiParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return Parameters.builder().build();
        }
        return Parameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }
    public static Message toQianfanMessage(ChatMessage message) {

        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return Message.builder()
                    .role(Role.USER)
                    .content(userMessage.text())
                    .name(userMessage.name())
                    .build();
        }

        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (!aiMessage.hasToolExecutionRequests()) {

                return  Message.builder()
                        .content(message.text())
                        .role(Role.ASSISTANT)
                        .build();
            }

            ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
            if (toolExecutionRequest.id() == null) {
                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolExecutionRequest.name())
                        .arguments(toolExecutionRequest.arguments())
                        .build();

                return Message.builder()
                        .content(message.text())
                        .role(Role.ASSISTANT)
                        .functionCall(functionCall)
                        .build();
            }

        }
        if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;

                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolExecutionResultMessage.toolName())
                        .arguments(toolExecutionResultMessage.text())
                        .build();
                return  Message.builder()
                        .content(message.text())
                        .role(Role.FUNCTION)
                        .name(functionCall.name())
                        .build();

        }
        throw illegalArgument("Unknown message type: " + message.type());
    }
    static TokenUsage tokenUsageFrom(ChatCompletionResponse response) {
        return Optional.of(response)
                .map(ChatCompletionResponse::getUsage)
                .map(usage -> new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()))
                .orElse(null);
    }

    static TokenUsage tokenUsageFrom(CompletionResponse response) {
        return Optional.of(response)
                .map(CompletionResponse::getUsage)
                .map(usage -> new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()))
                .orElse(null);
    }


    static TokenUsage tokenUsageFrom(EmbeddingResponse response) {
        return Optional.of(response)
                .map(EmbeddingResponse::getUsage)
                .map(usage -> new TokenUsage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()))
                .orElse(null);
    }


    public static FinishReason finishReasonFrom(String finishReason) {

        if(Utils.isNullOrBlank(finishReason)){
            return null;
        }

        switch (finishReason) {
            case "normal":
                return STOP;
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "content_filter":
                return CONTENT_FILTER;
            case "function_call":
                return TOOL_EXECUTION;
            default:
                return null;
        }
    }
    public static AiMessage aiMessageFrom(ChatCompletionResponse response) {

        FunctionCall functionCall = response.getFunctionCall();

        if (functionCall != null) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.name())
                    .arguments(functionCall.arguments())
                    .build();
            return aiMessage(toolExecutionRequest);
        }

        return aiMessage(response.getResult());
    }


    static String getSystemMessage(List<ChatMessage> messages) {

        List<ChatMessage> systemMessages = messages.stream().filter(message -> message instanceof SystemMessage).collect(toList());

        if (systemMessages.size() > 1) {
            throw new RuntimeException("Multiple system messages are not supported");
        }

        if(Utils.isNullOrEmpty(systemMessages)){
            return  null;
        }

        return ((SystemMessage) systemMessages.get(0)).text();

    }
    public static List<Message> toOpenAiMessages(List<ChatMessage> messages) {
         List<Message> aiMessages = messages.stream()
                .filter(chatMessage -> !(chatMessage instanceof SystemMessage))
                .map(InternalQianfanHelper::toQianfanMessage)
                .collect(toList());

         // ensure the length of messages is odd
        if(aiMessages.size() == 0 || aiMessages.size() % 2 == 1){
            return aiMessages;
        }

        aiMessages.remove(0);
        return aiMessages;
    }


}
