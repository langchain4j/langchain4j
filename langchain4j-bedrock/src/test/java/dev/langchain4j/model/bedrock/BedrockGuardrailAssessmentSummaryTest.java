package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseTrace;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTraceAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.PromptRouterTrace;

class BedrockGuardrailAssessmentSummaryTest {

    private final BedrockChatModel model = BedrockChatModel.builder()
            .client(mock(BedrockRuntimeClient.class))
            .modelId("test-model")
            .build();

    @Test
    void should_return_null_when_trace_is_null() {
        assertThat(model.guardrailAssessmentSummaryFrom(null)).isNull();
    }

    @Test
    void should_return_null_when_trace_has_no_guardrail() {

        ConverseTrace trace = ConverseTrace.builder()
                .promptRouter(PromptRouterTrace.builder()
                        .invokedModelId("arn:aws:bedrock:us-east-1::foundation-model/model")
                        .build())
                .build();

        assertThat(model.guardrailAssessmentSummaryFrom(trace)).isNull();
    }

    @Test
    void should_return_summary_when_guardrail_is_present() {

        ConverseTrace trace = ConverseTrace.builder()
                .guardrail(GuardrailTraceAssessment.builder().build())
                .build();

        GuardrailAssessmentSummary summary = model.guardrailAssessmentSummaryFrom(trace);

        assertThat(summary).isNotNull();
        assertThat(summary.hasAssessments()).isFalse();
    }
}
