package com.example.support;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Scripts for ChatModelMock / StreamingChatModelMock: what the mocked model answers in each step of an invocation.
 */
final class ModelScripts {

    private ModelScripts() {}

    static AiMessage toolCall(String toolName, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(toolName)
                .arguments(arguments)
                .build());
    }

    /**
     * Requests the given tool calls one after the other, then answers. The step is the number of model responses
     * since the last user message, so that the script restarts with every new message in the chat memory.
     */
    static Function<ChatRequest, AiMessage> callToolsThenAnswer(String answer, AiMessage... toolCalls) {
        return callToolsThenAnswer(toolResults -> answer, toolCalls);
    }

    /**
     * Like {@link #callToolsThenAnswer(String, AiMessage...)}, with an answer computed from the tool results.
     */
    static Function<ChatRequest, AiMessage> callToolsThenAnswer(
            Function<List<String>, String> answer, AiMessage... toolCalls) {
        return request -> {
            List<ChatMessage> sinceUserMessage = sinceLastUserMessage(request.messages());
            long step = sinceUserMessage.stream()
                    .filter(AiMessage.class::isInstance)
                    .count();
            if (step < toolCalls.length) {
                return toolCalls[(int) step];
            }
            List<String> toolResults = sinceUserMessage.stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(message -> ((ToolExecutionResultMessage) message).text())
                    .toList();
            return AiMessage.from(answer.apply(toolResults));
        };
    }

    static String lastUserMessage(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        throw new IllegalStateException("No user message in " + messages);
    }

    private static List<ChatMessage> sinceLastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return messages.subList(i + 1, messages.size());
            }
        }
        return messages;
    }
}
