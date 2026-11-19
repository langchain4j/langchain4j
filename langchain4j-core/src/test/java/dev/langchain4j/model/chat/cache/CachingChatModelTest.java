package dev.langchain4j.model.chat.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingChatModelTest {

    static class CountingChatModel implements ChatModel {

        final AtomicInteger calls = new AtomicInteger();
        final String modelName;
        RuntimeException error;

        CountingChatModel(String modelName) {
            this.modelName = modelName;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            int call = calls.incrementAndGet();
            if (error != null) {
                throw error;
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(modelName + " answer " + call))
                    .build();
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().modelName(modelName).build();
        }
    }

    static ChatRequest request(String text, double temperature) {
        return ChatRequest.builder()
                .messages(UserMessage.from(text))
                .temperature(temperature)
                .build();
    }

    @Test
    void should_return_cached_response_for_equal_request() {
        CountingChatModel model = new CountingChatModel("model");
        ChatModel caching = new CachingChatModel(model, new InMemoryChatResponseCache(10));

        ChatResponse first = caching.chat(request("hi", 0.0));
        ChatResponse second = caching.chat(request("hi", 0.0));

        assertThat(second).isEqualTo(first);
        assertThat(model.calls).hasValue(1);
    }

    @Test
    void should_call_model_when_messages_or_parameters_differ() {
        CountingChatModel model = new CountingChatModel("model");
        ChatModel caching = new CachingChatModel(model, new InMemoryChatResponseCache(10));

        caching.chat(request("hi", 0.0));
        caching.chat(request("hello", 0.0));
        caching.chat(request("hi", 0.7));

        assertThat(model.calls).hasValue(3);
    }

    @Test
    void should_not_share_responses_between_models_using_the_same_cache() {
        ChatResponseCache cache = new InMemoryChatResponseCache(10);
        ChatModel small = new CachingChatModel(new CountingChatModel("small"), cache);
        ChatModel large = new CachingChatModel(new CountingChatModel("large"), cache);

        assertThat(small.chat(request("hi", 0.0)).aiMessage().text()).isEqualTo("small answer 1");
        assertThat(large.chat(request("hi", 0.0)).aiMessage().text()).isEqualTo("large answer 1");
    }

    @Test
    void should_not_cache_failed_calls() {
        CountingChatModel model = new CountingChatModel("model");
        model.error = new RuntimeException("boom");
        ChatModel caching = new CachingChatModel(model, new InMemoryChatResponseCache(10));

        assertThatThrownBy(() -> caching.chat(request("hi", 0.0))).hasMessage("boom");
        model.error = null;
        caching.chat(request("hi", 0.0));

        assertThat(model.calls).hasValue(2);
    }

    @Test
    void should_evict_least_recently_used_response() {
        ChatResponseCache cache = new InMemoryChatResponseCache(1);
        ChatResponse response =
                ChatResponse.builder().aiMessage(AiMessage.from("a")).build();

        cache.put(request("first", 0.0), response);
        cache.put(request("second", 0.0), response);

        assertThat(cache.get(request("first", 0.0))).isNull();
        assertThat(cache.get(request("second", 0.0))).isEqualTo(response);
    }
}
