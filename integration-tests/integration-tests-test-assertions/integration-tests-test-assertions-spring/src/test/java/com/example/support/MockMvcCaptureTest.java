package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.support.refund.AuditLog;
import dev.langchain4j.test.service.ChatModelCall;
import dev.langchain4j.test.service.AiServiceCapture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Same configuration as {@link SupportControllerMockMvcTest}, so the Spring context is reused from the cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestModels.class)
class MockMvcCaptureTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuditLog auditLog;

    @Test
    void output_is_the_result_actions_of_the_request_and_the_invocation_has_the_tool_loop() throws Exception {
        AiServiceCapture capture = capture(() -> mockMvc.perform(
                post("/support/chat").contentType("text/plain").content("Please split refund MS-330 30 20")));

        ResultActions response = capture.lambdaOutput(ResultActions.class);
        response.andExpect(status().isOk())
                .andExpect(content().string("Done: Refund of 20 EUR issued for booking MS-330"));
        assertThat(capture.outputText()).isEqualTo("Done: Refund of 20 EUR issued for booking MS-330");
        assertThat(capture)
                .hasSucceeded()
                .hasToolExecutedTimes("issueRefund", 2)
                .hasToolsExecutedInOrder("getBookingDetails", "issueRefund", "issueRefund")
                .hasOwnChatModelCallCount(4)
                .hasOwnChatModelCallCountAtMost(4)
                .hasNoRetrievedSources();
        assertThat(capture.invocation().ownChatModelCalls())
                .allSatisfy(call -> assertThat(call.request().toolSpecifications()).isNotEmpty())
                .extracting(ChatModelCall::request)
                .doesNotContainNull();
        assertThat(auditLog.entries()).contains("issueRefund[MS-330, 30]", "issueRefund[MS-330, 20]");
    }

    @Test
    void http_assertions_inside_the_captured_code() {
        AiServiceCapture capture = capture(() -> mockMvc.perform(
                        post("/support/chat").contentType("text/plain").content("What is your return policy?"))
                .andExpect(status().isOk())
                .andExpect(content().string(SupportScript.POLICY_ANSWER))
                .andReturn());

        MvcResult result = capture.lambdaOutput(MvcResult.class);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(capture)
                .hasRetrievedSourceContaining("14 days")
                .hasToolExecutedTimes("issueRefund", 0)
                .hasOwnChatModelCallCount(1);
    }

    @Test
    void sse_controller_consumes_the_token_stream_itself() throws Exception {
        AiServiceCapture capture = capture(() -> mockMvc.perform(get("/support/stream").param("message", "Hi"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture).hasSucceeded().hasOwnChatModelCallCount(1);

        MvcResult result = capture.lambdaOutput(MvcResult.class);
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        assertThat(result.getResponse().getContentAsString()).contains("data:H");
    }

    @Test
    @Tag("reactor")
    void flux_controller_is_subscribed_by_spring_mvc() throws Exception {
        AiServiceCapture capture = capture(() -> mockMvc.perform(get("/support/flux").param("message", "Hi"))
                .andReturn());

        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);

        mockMvc.perform(asyncDispatch(capture.lambdaOutput(MvcResult.class))).andExpect(status().isOk());
    }
}
