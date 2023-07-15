package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;

import java.util.Collection;
import java.util.List;

import static dev.ai4j.openai4j.chat.Role.*;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static java.util.stream.Collectors.toList;

class OpenAiConverters {

    static List<Message> toOpenAiMessages(List<ChatMessage> messages) {

        return messages.stream()
                .map(OpenAiConverters::toOpenAiMessage)
                .collect(toList());
    }

    static Message toOpenAiMessage(ChatMessage message) {

        return Message.builder()
                .role(roleFrom(message))
                .name(nameFrom(message))
                .content(message.text())
                .functionCall(functionCallFrom(message))
                .build();
    }

    private static String nameFrom(ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).name();
        }

        if (message instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) message).toolName();
        }

        return null;
    }

    private static FunctionCall functionCallFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.toolExecutionRequest() != null) {
                return FunctionCall.builder()
                        .name(aiMessage.toolExecutionRequest().name())
                        .arguments(aiMessage.toolExecutionRequest().arguments())
                        .build();
            }
        }

        return null;
    }

    static Role roleFrom(ChatMessage message) {
        if (message instanceof AiMessage) {
            return ASSISTANT;
        } else if (message instanceof ToolExecutionResultMessage) {
            return FUNCTION;
        } else if (message instanceof SystemMessage) {
            return SYSTEM;
        } else {
            return USER;
        }
    }

    static List<Function> toFunctions(Collection<ToolSpecification> toolSpecifications) {
        if (toolSpecifications == null) {
            return null;
        }

        return toolSpecifications.stream()
                .map(OpenAiConverters::toFunction)
                .collect(toList());
    }

    private static Function toFunction(ToolSpecification toolSpecification) {
        return Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description())
                .parameters(toOpenAiParameters(toolSpecification.parameters()))
                .build();
    }

    private static dev.ai4j.openai4j.chat.Parameters toOpenAiParameters(ToolParameters toolParameters) {
        if (toolParameters == null) {
            return dev.ai4j.openai4j.chat.Parameters.builder().build();
        }
        return dev.ai4j.openai4j.chat.Parameters.builder()
                .properties(toolParameters.properties())
                .required(toolParameters.required())
                .build();
    }

    static AiMessage aiMessageFrom(ChatCompletionResponse response) {
        if (response.content() != null) {
            return aiMessage(response.content());
        } else {
            FunctionCall functionCall = response.choices().get(0).message().functionCall();

            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(functionCall.name())
                    .arguments(functionCall.arguments())
                    .build();

            return aiMessage(toolExecutionRequest);
        }
    }
}
