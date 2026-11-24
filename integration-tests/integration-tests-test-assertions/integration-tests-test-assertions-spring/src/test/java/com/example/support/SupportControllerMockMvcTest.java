package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.assistant.StreamingSupportAssistant;
import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestModels.class)
class SupportControllerMockMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void records_the_invocation_of_a_mock_mvc_request_because_it_runs_on_the_test_thread() throws Exception {
        AiServiceInvocation invocation = capture(() -> mockMvc.perform(
                                post("/support/chat").contentType("text/plain").content("Please cancel MS-777"))
                        .andExpect(status().isOk())
                        .andExpect(content().string("Done: Booking MS-777 cancelled"))
                        .andReturn())
                .invocation();

        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"));
        assertThat(invocation.invocationContext().interfaceName()).isEqualTo(SupportAssistant.class.getName());
    }

    @Test
    void records_a_failed_invocation_that_the_controller_advice_turned_into_an_error_response() {
        AiServiceInvocation invocation = captureExpectingFailure(() -> mockMvc.perform(
                                post("/support/chat").contentType("text/plain").content("leak something"))
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(content().string("Sorry, I cannot answer that."))
                        .andReturn())
                .invocation();

        assertThat(invocation)
                .hasFailedWith(OutputGuardrailException.class)
                .hasFailedWithMessageContaining("The response leaks internal notes");
    }

    @Test
    void capture_throws_the_failure_even_when_the_controller_advice_handled_it() {
        // documented: a failed invocation makes capture(...) throw even when the code recovered from it
        assertThatThrownBy(() -> capture(() -> mockMvc.perform(
                                post("/support/chat").contentType("text/plain").content("leak something"))
                        .andExpect(status().isUnprocessableEntity())
                        .andReturn()))
                .isInstanceOf(OutputGuardrailException.class);
    }

    @Test
    void records_a_token_stream_that_the_controller_starts_on_a_task_executor() throws Exception {
        MvcResult[] result = new MvcResult[1];
        AiServiceInvocation invocation = capture(() -> result[0] = mockMvc.perform(
                                get("/support/stream").param("message", "Hi"))
                        .andReturn())
                .invocation();

        assertThat(invocation.invocationContext().interfaceName())
                .isEqualTo(StreamingSupportAssistant.class.getName());
        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation.tokenUsage().totalTokenCount()).isLessThanOrEqualTo(30);

        mockMvc.perform(asyncDispatch(result[0])).andExpect(status().isOk());
        assertThat(result[0].getResponse().getContentAsString()).contains("data:H");
    }

    @Test
    @Tag("reactor")
    void records_a_flux_returned_by_the_controller() throws Exception {
        MvcResult[] result = new MvcResult[1];
        AiServiceInvocation invocation = capture(() -> result[0] = mockMvc.perform(
                                get("/support/flux").param("message", "Hi"))
                        .andReturn())
                .invocation();

        assertThat(invocation.invocationContext().methodName()).isEqualTo("chatFlow");
        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);

        mockMvc.perform(asyncDispatch(result[0])).andExpect(status().isOk());
        assertThat(result[0].getResponse().getContentAsString()).contains("data:");
    }

    @Test
    void does_not_capture_a_request_handled_by_an_async_method_and_says_why() {
        AiServiceCapture invocations = capture(() -> mockMvc.perform(
                                post("/support/chat-async").contentType("text/plain").content("Please cancel MS-778"))
                        .andReturn());

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No AI Service invocation was captured")
                .hasMessageContaining("@Async");
    }
}
