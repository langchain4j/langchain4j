package dev.langchain4j.model.watsonx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WatsonxChatRequestParametersTest {

    private static WatsonxChatRequestParameters.Builder fullyPopulated() {
        return WatsonxChatRequestParameters.builder()
                // common (parent) fields
                .modelName("ibm/granite-3-8b-instruct")
                .temperature(0.7)
                .maxOutputTokens(100)
                // watsonx-specific fields
                .projectId("project-id")
                .spaceId("space-id")
                .logitBias(Map.of("token", 1))
                .logprobs(true)
                .topLogprobs(5)
                .seed(42)
                .toolChoiceName("tool")
                .timeout(Duration.ofSeconds(10))
                .guidedChoice(Set.of("a", "b"))
                .guidedRegex("[0-9]+")
                .guidedGrammar("grammar")
                .repetitionPenalty(1.1)
                .lengthPenalty(1.2)
                .deploymentId("deployment-id");
    }

    @Test
    void equals_and_hashCode_should_include_watsonx_fields() {
        WatsonxChatRequestParameters params1 = fullyPopulated().build();
        WatsonxChatRequestParameters params2 = fullyPopulated().build();

        assertThat(params1).isEqualTo(params2);
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
    }

    @Test
    void equals_should_distinguish_each_watsonx_field() {
        WatsonxChatRequestParameters base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(fullyPopulated().projectId("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().spaceId("other").build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().logitBias(Map.of("token", 2)).build());
        assertThat(base).isNotEqualTo(fullyPopulated().logprobs(false).build());
        assertThat(base).isNotEqualTo(fullyPopulated().topLogprobs(9).build());
        assertThat(base).isNotEqualTo(fullyPopulated().seed(7).build());
        assertThat(base).isNotEqualTo(fullyPopulated().toolChoiceName("other").build());
        assertThat(base)
                .isNotEqualTo(fullyPopulated().timeout(Duration.ofSeconds(20)).build());
        assertThat(base).isNotEqualTo(fullyPopulated().guidedChoice(Set.of("c")).build());
        assertThat(base).isNotEqualTo(fullyPopulated().guidedRegex("[a-z]+").build());
        assertThat(base).isNotEqualTo(fullyPopulated().guidedGrammar("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().repetitionPenalty(2.0).build());
        assertThat(base).isNotEqualTo(fullyPopulated().lengthPenalty(2.0).build());
        assertThat(base).isNotEqualTo(fullyPopulated().deploymentId("other").build());
    }

    @Test
    void equals_should_still_honor_inherited_fields() {
        WatsonxChatRequestParameters base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(fullyPopulated().modelName("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().temperature(0.1).build());
    }

    @Test
    void toString_should_include_watsonx_fields() {
        String text = fullyPopulated().build().toString();

        assertThat(text)
                .contains("projectId=project-id")
                .contains("spaceId=space-id")
                .contains("seed=42")
                .contains("deploymentId=deployment-id");
    }
}
