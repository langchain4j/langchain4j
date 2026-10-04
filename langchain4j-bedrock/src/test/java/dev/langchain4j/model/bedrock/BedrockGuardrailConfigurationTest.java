package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.bedrock.BedrockGuardrailConfiguration.ProcessingMode;
import org.junit.jupiter.api.Test;

class BedrockGuardrailConfigurationTest {

    @Test
    void should_be_equal_when_all_fields_match() {
        BedrockGuardrailConfiguration first = fullyPopulated().build();
        BedrockGuardrailConfiguration second = fullyPopulated().build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void should_not_be_equal_when_guardrail_identifier_differs() {
        BedrockGuardrailConfiguration first =
                fullyPopulated().guardrailIdentifier("first").build();
        BedrockGuardrailConfiguration second =
                fullyPopulated().guardrailIdentifier("second").build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_when_guardrail_version_differs() {
        BedrockGuardrailConfiguration first = fullyPopulated().guardrailVersion("1").build();
        BedrockGuardrailConfiguration second = fullyPopulated().guardrailVersion("2").build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_when_stream_processing_mode_differs() {
        BedrockGuardrailConfiguration first =
                fullyPopulated().streamProcessingMode(ProcessingMode.SYNC).build();
        BedrockGuardrailConfiguration second =
                fullyPopulated().streamProcessingMode(ProcessingMode.ASYNC).build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_to_null_or_other_type() {
        BedrockGuardrailConfiguration configuration = fullyPopulated().build();

        assertThat(configuration).isNotEqualTo(null).isNotEqualTo("guardrail");
    }

    private static BedrockGuardrailConfiguration.Builder fullyPopulated() {
        return BedrockGuardrailConfiguration.builder()
                .guardrailIdentifier("guardrail")
                .guardrailVersion("1")
                .streamProcessingMode(ProcessingMode.SYNC);
    }
}
