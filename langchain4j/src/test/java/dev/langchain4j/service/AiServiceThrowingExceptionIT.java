package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.policy.InvocationPolicy;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServiceThrowingExceptionIT {
    interface ThrowingService {
        Result<AiMessage> chat(String userMessage);
    }

    @Test
    void with_no_policy() {

        AtomicInteger invocationCount = new AtomicInteger(0);

        ChatLanguageModel chatLanguageModel = new ChatModelMock(chatRequest -> {
            invocationCount.incrementAndGet();
            throw new HttpException(429, "Insufficient quota");
        });

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ThrowingService assistant = AiServices.builder(ThrowingService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        assertThrows(RuntimeException.class, () -> assistant.chat("hi"));
        assertThat(invocationCount.get()).isEqualTo(1);
    }

    @Test
    void with_default_policy() {

        AtomicInteger invocationCount = new AtomicInteger(0);

        ChatLanguageModel chatLanguageModel = new ChatModelMock(chatRequest -> {
            invocationCount.incrementAndGet();
            throw new HttpException(429, "Insufficient quota");
        }).withInvocationPolicy(InvocationPolicy.DEFAULT);

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ThrowingService assistant = AiServices.builder(ThrowingService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        assertThrows(RuntimeException.class, () -> assistant.chat("hi"));
        assertThat(invocationCount.get()).isEqualTo(3);
    }
}
