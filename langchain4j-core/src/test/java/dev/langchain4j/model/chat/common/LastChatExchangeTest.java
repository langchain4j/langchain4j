package dev.langchain4j.model.chat.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LastChatExchangeTest {

    private final LastChatExchange lastChatExchange = new LastChatExchange();

    @BeforeEach
    void beforeEach() {
        lastChatExchange.beforeEach(null);
    }

    @Test
    void should_append_last_request_and_response_to_the_failure() {

        LastChatExchange.recordRequest(
                ChatRequest.builder().messages(UserMessage.from("What is the weather?")).build());
        LastChatExchange.recordResponse(
                ChatResponse.builder().aiMessage(AiMessage.from("It is sunny")).build());

        AssertionError failure = new AssertionError("Expecting actual not to be null");

        assertThatThrownBy(() -> lastChatExchange.handleTestExecutionException(null, failure))
                .isExactlyInstanceOf(AssertionError.class)
                .hasCause(failure)
                .hasMessageContaining("Expecting actual not to be null")
                .hasMessageContaining("What is the weather?")
                .hasMessageContaining("It is sunny");
    }

    @Test
    void should_report_that_there_is_no_response_when_the_llm_call_failed() {

        LastChatExchange.recordRequest(
                ChatRequest.builder().messages(UserMessage.from("What is the weather?")).build());

        RuntimeException failure = new RuntimeException("timeout");

        assertThatThrownBy(() -> lastChatExchange.handleTestExecutionException(null, failure))
                .hasCause(failure)
                .hasMessageContaining("What is the weather?")
                .hasMessageContaining("did not return a response");
    }

    @Test
    void should_rethrow_the_original_failure_when_there_was_no_llm_call() {

        AssertionError failure = new AssertionError("Expecting actual not to be null");

        assertThatThrownBy(() -> lastChatExchange.handleTestExecutionException(null, failure))
                .isSameAs(failure);
    }
}
