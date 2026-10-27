package dev.langchain4j.test;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;

import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jAssertionsTest {

    @Test
    void should_offer_langchain4j_and_assertj_assertions_with_one_import() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Cancel booking MS-777"))
                .build();

        assertThat(request).hasUserMessageContaining("MS-777");
        assertThat("Booking cancelled").contains("cancelled");
        assertThat(InputGuardrailResult.success()).isSuccessful();
        assertThat(OutputGuardrailResult.success()).isSuccessful();
        assertThat(List.of(1, 2)).containsExactly(1, 2);
        assertThatThrownBy(() -> assertThat(request).hasUserMessageContaining("MS-778"))
                .isInstanceOf(AssertionError.class);
    }
}
