package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Assertions on the sources retrieved for retrieval-augmented generation (RAG).
 */
@Timeout(30)
class RetrievedSourcesTest {

    ChatModelMock model = ChatModelMock.thatResponds("Small pets are welcome for 10 EUR per day.");

    PolicyExpert policyExpert = CustomerSupport.policyExpert(model);

    @Test
    void should_capture_retrieved_sources() {
        AiServiceInvocation invocation = capture(() -> policyExpert.answer("Can I bring my pets?")).invocation();

        assertThat(invocation).hasRetrievedSourceContaining("10 EUR per day");
        assertThat(invocation.sources())
                .extracting(source -> source.textSegment().text())
                .containsExactly("Pets: small pets are allowed in all rooms for a fee of 10 EUR per day.");
        // the retrieved source was given to the model
        assertThat(ModelScripts.lastUserMessage(model.request())).contains("10 EUR per day");
    }

    @Test
    void should_list_retrieved_sources_when_none_contains_the_text() {
        AiServiceInvocation invocation =
                capture(() -> policyExpert.answer("How do refunds work for cancellation?")).invocation();

        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("pets"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a retrieved source containing <pets>, but there was none.")
                .hasMessageContaining("Retrieved sources:")
                .hasMessageContaining("  - Cancellation: bookings can be cancelled free of charge")
                .hasMessageContaining("  - Refunds: refunds are paid to the original payment method");
    }

    @Test
    void should_tell_when_nothing_was_retrieved() {
        AiServiceInvocation invocation = capture(() -> policyExpert.answer("Hi")).invocation();

        assertThat(invocation.sources()).isEmpty();
        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("pets"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No sources were retrieved, or the AI Service has no retrieval augmentor.");
    }

    @Test
    void should_tell_when_the_ai_service_has_no_retrieval_augmentor() {
        CustomerSupportAgent agent =
                CustomerSupport.customerSupportAgent(ChatModelMock.thatResponds("Hi"), Bookings.bookings(), policyExpert);

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Can I bring my pets?")).invocation();

        assertThatThrownBy(invocation::sources).hasMessageContaining("has no retrieval augmentor");
        assertThatThrownBy(() -> assertThat(invocation).hasRetrievedSourceContaining("pets"))
                .isInstanceOf(AssertionError.class)
                .hasMessage(
                        "[CustomerSupportAgent.chat] Expected a retrieved source containing <pets>, but no sources were captured: the AI Service has no retrieval augmentor, or it does not report what it retrieves");
    }

    @Test
    void should_check_sources_of_a_result() {
        AiServiceCapture result = capture(() -> policyExpert.answerWithSources("Can I bring my pets?"));

        assertThat(result.invocation().sources()).extracting(Content::textSegment).hasSize(1);
        assertThat(result).hasRetrievedSourceContaining("10 EUR per day").hasNoToolExecutions();
    }

    @Test
    void should_capture_sources_of_result_returning_method() {
        AiServiceInvocation invocation =
                capture(() -> policyExpert.answerWithSources("Can I bring my pets?")).invocation();

        assertThat(invocation.outputText()).isEqualTo("Small pets are welcome for 10 EUR per day.");
        assertThat(invocation).hasRetrievedSourceContaining("Pets:");
    }
}
