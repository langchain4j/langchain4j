package dev.langchain4j.test;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;

import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jAssertionsTest {

    interface Assistant {

        String chat(String message);
    }

    @Test
    void should_offer_langchain4j_and_assertj_assertions_with_one_import() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds("Booking cancelled"))
                .build();

        AiServiceInvocation invocation =
                AiServiceCapture.capture(() -> assistant.chat("Cancel booking MS-777")).invocation();

        assertThat(invocation).hasNoToolExecutions();
        assertThat(invocation.outputText()).contains("cancelled");
        assertThat(InputGuardrailResult.success()).isSuccessful();
        assertThat(OutputGuardrailResult.success()).isSuccessful();
        assertThat(List.of(1, 2)).containsExactly(1, 2);
        assertThatThrownBy(() -> assertThat(invocation).hasToolExecuted("cancelBooking"))
                .isInstanceOf(AssertionError.class);
    }
}
