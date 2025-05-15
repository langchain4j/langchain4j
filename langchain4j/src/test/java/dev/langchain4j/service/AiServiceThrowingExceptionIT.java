package dev.langchain4j.service;

import static dev.langchain4j.internal.RetryUtils.DEFAULT_RETRY_POLICY;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class AiServiceThrowingExceptionIT {
    interface ThrowingService {
        Result<AiMessage> chat(String userMessage);
    }

    @Test
    void with_no_retry() {

        AtomicInteger invocationCount = new AtomicInteger(0);

        ChatModel chatModel = new ChatModelMock(chatRequest -> {
            invocationCount.incrementAndGet();
            throw new HttpException(429, "Insufficient quota");
        });

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ThrowingService assistant = AiServices.builder(ThrowingService.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(RateLimitException.class)
                .hasMessageContaining("Insufficient quota");

        assertThat(invocationCount.get()).isEqualTo(1);
    }

    @Test
    void with_recoverable_retry() {

        AtomicInteger invocationCount = new AtomicInteger(0);

        ChatModel chatModel = new ChatModelMock(chatRequest -> {
                    invocationCount.incrementAndGet();
                    throw new HttpException(429, "Insufficient quota");
                })
                .withRetryPolicy(DEFAULT_RETRY_POLICY);

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ThrowingService assistant = AiServices.builder(ThrowingService.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(RateLimitException.class)
                .hasMessageContaining("Insufficient quota");

        assertThat(invocationCount.get()).isEqualTo(3);
    }

    @Test
    void with_unrecoverable_retry() {

        AtomicInteger invocationCount = new AtomicInteger(0);

        ChatModel chatModel = new ChatModelMock(chatRequest -> {
                    invocationCount.incrementAndGet();
                    throw new HttpException(404, "Not Found");
                })
                .withRetryPolicy(DEFAULT_RETRY_POLICY);

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ThrowingService assistant = AiServices.builder(ThrowingService.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("Not Found");

        assertThat(invocationCount.get()).isEqualTo(1);
    }

    @Test
    void with_wrong_url() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl("https://api.openai.com/v0")
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        ThrowingService assistant =
                AiServices.builder(ThrowingService.class).chatModel(chatModel).build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("Not Found");
    }

    @Test
    void with_wrong_key() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey("xyz")
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        ThrowingService assistant =
                AiServices.builder(ThrowingService.class).chatModel(chatModel).build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Incorrect API key provided: xyz.");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void with_wrong_model() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("gpt-0")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        ThrowingService assistant =
                AiServices.builder(ThrowingService.class).chatModel(chatModel).build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("The model `gpt-0` does not exist or you do not have access to it.");
    }
}
