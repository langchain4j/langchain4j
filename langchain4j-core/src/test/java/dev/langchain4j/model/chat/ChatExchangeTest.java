package dev.langchain4j.model.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class ChatExchangeTest {

    static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hi")).build();
    static final ChatResponse RESPONSE =
            ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build();

    @Test
    void should_hold_request_and_response() {

        ChatExchange exchange = new ChatExchange(REQUEST, RESPONSE);

        assertThat(exchange.request()).isSameAs(REQUEST);
        assertThat(exchange.response()).isSameAs(RESPONSE);
    }

    @Test
    void should_allow_unknown_request() {

        ChatExchange exchange = new ChatExchange(null, RESPONSE);

        assertThat(exchange.request()).isNull();
        assertThat(exchange).isEqualTo(new ChatExchange(null, RESPONSE)).isNotEqualTo(new ChatExchange(REQUEST, RESPONSE));
    }

    @Test
    void should_require_response() {

        assertThatThrownBy(() -> new ChatExchange(REQUEST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("response");
    }

    @Test
    void equal_exchanges_should_have_the_same_hash_code() {

        assertThat(new ChatExchange(REQUEST, RESPONSE))
                .isEqualTo(new ChatExchange(REQUEST, RESPONSE))
                .hasSameHashCodeAs(new ChatExchange(REQUEST, RESPONSE));
    }
}
