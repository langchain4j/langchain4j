package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.internal.Utils.randomString;
import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicCachingProxyIT {

    @Test
    void should_cache_sync_chat_responses() {

        // given
        AnthropicChatModel nonCachingModel = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest nonCachingRequest = ChatRequest.builder()
                .messages(UserMessage.from(randomString(10)))
                .build();

        // when
        ChatResponse nonCachedResponse1 = nonCachingModel.chat(nonCachingRequest);
        ChatResponse nonCachedResponse2 = nonCachingModel.chat(nonCachingRequest);

        // then
        assertThat(nonCachedResponse1.metadata().id()).isNotEqualTo(nonCachedResponse2.metadata().id());

        // given
        AnthropicChatModel cachingModel = AnthropicChatModel.builder()
                .baseUrl("http://langchain4j.dev:8083/v1")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest cachingRequest = ChatRequest.builder()
                .messages(UserMessage.from(randomString(10)))
                .build();

        // when
        ChatResponse cachedResponse1 = cachingModel.chat(cachingRequest);
        ChatResponse cachedResponse2 = cachingModel.chat(cachingRequest);

        // then
        assertThat(cachedResponse1.metadata().id()).isEqualTo(cachedResponse2.metadata().id());
    }

    @Test
    void should_cache_streaming_chat_responses() {

        AnthropicStreamingChatModel nonCachingModel = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest nonCachingRequest = ChatRequest.builder()
                .messages(UserMessage.from(randomString(10)))
                .build();


        // when
        TestStreamingChatResponseHandler handler1 = new TestStreamingChatResponseHandler();
        nonCachingModel.chat(nonCachingRequest, handler1);
        ChatResponse nonCachedResponse1 = handler1.get();

        TestStreamingChatResponseHandler handler2 = new TestStreamingChatResponseHandler();
        nonCachingModel.chat(nonCachingRequest, handler2);
        ChatResponse nonCachedResponse2 = handler2.get();

        // then
        assertThat(nonCachedResponse1.metadata().id()).isNotEqualTo(nonCachedResponse2.metadata().id());

        // given
        AnthropicStreamingChatModel cachingModel = AnthropicStreamingChatModel.builder()
                .baseUrl("http://langchain4j.dev:8083/v1")
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(10)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatRequest cachingRequest = ChatRequest.builder()
                .messages(UserMessage.from(randomString(10)))
                .build();

        // when
        TestStreamingChatResponseHandler handler3 = new TestStreamingChatResponseHandler();
        cachingModel.chat(cachingRequest, handler3);
        ChatResponse cachedResponse1 = handler3.get();

        TestStreamingChatResponseHandler handler4 = new TestStreamingChatResponseHandler();
        cachingModel.chat(cachingRequest, handler4);
        ChatResponse cachedResponse2 = handler4.get();

        // then
        assertThat(cachedResponse1.metadata().id()).isEqualTo(cachedResponse2.metadata().id());
    }
}
