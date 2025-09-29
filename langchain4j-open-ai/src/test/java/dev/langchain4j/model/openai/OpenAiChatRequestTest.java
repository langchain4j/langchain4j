package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class OpenAiChatRequestTest {

    private static final double TEMPERATURE = 1.0;
    private static final int SEED = 123;

    @Test
    void should_set_common_parameters_then_OpenAI_specific_parameters() {

        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .temperature(TEMPERATURE) // first set common parameters
                .seed(SEED) // then set OpenAI-specific parameters
                .build();

        // then
        assertThat(parameters.temperature()).isEqualTo(TEMPERATURE);
        assertThat(parameters.seed()).isEqualTo(SEED);
    }

    @Test
    void should_set_OpenAI_specific_parameters_then_common_parameters() {

        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .seed(SEED) // first set OpenAI-specific parameters
                .temperature(TEMPERATURE) // then set common parameters
                .build();

        // then
        assertThat(parameters.seed()).isEqualTo(SEED);
        assertThat(parameters.temperature()).isEqualTo(TEMPERATURE);
    }

    @Test
    void should_use_empty_constant() {
        // when
        OpenAiChatRequestParameters empty = OpenAiChatRequestParameters.EMPTY;

        // then
        assertThat(empty.maxCompletionTokens()).isNull();
        assertThat(empty.seed()).isNull();
        assertThat(empty.user()).isNull();
        // verify it's the same instance when accessed multiple times
        assertThat(OpenAiChatRequestParameters.EMPTY).isSameAs(empty);
    }

    @Test
    void should_handle_zero_values() {
        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .maxCompletionTokens(0)
                .seed(0)
                .build();

        // then
        assertThat(parameters.maxCompletionTokens()).isEqualTo(0);
        assertThat(parameters.seed()).isEqualTo(0);
    }

    @Test
    void should_handle_large_values() {
        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .maxCompletionTokens(Integer.MAX_VALUE)
                .seed(Integer.MAX_VALUE)
                .build();

        // then
        assertThat(parameters.maxCompletionTokens()).isEqualTo(Integer.MAX_VALUE);
        assertThat(parameters.seed()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void should_handle_boolean_false_values() {
        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .parallelToolCalls(false)
                .store(false)
                .build();

        // then
        assertThat(parameters.parallelToolCalls()).isFalse();
        assertThat(parameters.store()).isFalse();
    }

    @Test
    void should_handle_empty_maps() {
        // when
        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .logitBias(new HashMap<>())
                .metadata(new HashMap<>())
                .customParameters(new HashMap<>())
                .build();

        // then
        assertThat(parameters.logitBias()).isEmpty();
        assertThat(parameters.metadata()).isEmpty();
        assertThat(parameters.customParameters()).isEmpty();
    }

    @Test
    void should_override_with_other_openai_parameters() {
        // given
        OpenAiChatRequestParameters original = OpenAiChatRequestParameters.builder()
                .seed(1)
                .user("original-user")
                .maxCompletionTokens(2)
                .build();

        OpenAiChatRequestParameters override = OpenAiChatRequestParameters.builder()
                .seed(2)
                .user("override-user")
                .parallelToolCalls(true)
                .build();

        // when
        OpenAiChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result.seed()).isEqualTo(2); // overridden
        assertThat(result.user()).isEqualTo("override-user"); // overridden
        assertThat(result.maxCompletionTokens()).isEqualTo(2); // kept from original
        assertThat(result.parallelToolCalls()).isTrue(); // new from override
    }

    @Test
    void should_not_be_equal_when_fields_differ() {
        // given
        OpenAiChatRequestParameters params1 =
                OpenAiChatRequestParameters.builder().seed(100).build();

        OpenAiChatRequestParameters params2 =
                OpenAiChatRequestParameters.builder().seed(200).build();

        // then
        assertThat(params1).isNotEqualTo(params2);
        assertThat(params1.hashCode()).isNotEqualTo(params2.hashCode());
    }

    @Test
    void should_not_be_equal_to_null() {
        // given
        OpenAiChatRequestParameters params =
                OpenAiChatRequestParameters.builder().build();

        // then
        assertThat(params).isNotEqualTo(null);
    }

    @Test
    void should_not_be_equal_to_different_class() {
        // given
        OpenAiChatRequestParameters params =
                OpenAiChatRequestParameters.builder().build();

        // then
        assertThat(params).isNotEqualTo("not a parameters object");
    }
}
