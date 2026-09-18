package dev.langchain4j.model.watsonx;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Think;
import com.ibm.watsonx.ai.chat.model.Thinking;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
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
                .thinking(Thinking.of(ThinkingEffort.LOW))
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
                .lengthPenalty(1.2);
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
                .isNotEqualTo(fullyPopulated()
                        .thinking(Thinking.of(ThinkingEffort.HIGH))
                        .build());
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
    }

    @Test
    void equals_should_still_honor_inherited_fields() {
        WatsonxChatRequestParameters base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(fullyPopulated().modelName("other").build());
        assertThat(base).isNotEqualTo(fullyPopulated().temperature(0.1).build());
    }

    @Test
    void equals_should_reject_null_and_other_types() {
        WatsonxChatRequestParameters base = fullyPopulated().build();

        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("not a parameters object");
    }

    @Test
    void overrideWith_and_defaultedBy_should_be_mirror_images() {
        WatsonxChatRequestParameters base = fullyPopulated().build();
        WatsonxChatRequestParameters other =
                WatsonxChatRequestParameters.builder().projectId("other").build();

        // overrideWith lets the argument win (it is declared on the interface, so it returns the interface type)
        var overridden = (WatsonxChatRequestParameters) base.overrideWith(other);
        assertThat(overridden.projectId()).isEqualTo("other");
        // defaultedBy lets the receiver win, and fills the gaps from the argument
        assertThat(other.defaultedBy(base).projectId()).isEqualTo("other");
        assertThat(other.defaultedBy(base).spaceId()).isEqualTo("space-id");
        assertThat(other.defaultedBy(base).seed()).isEqualTo(42);
    }

    @Test
    void thinking_should_be_disabled_by_a_null_argument() {
        assertThat(WatsonxChatRequestParameters.builder()
                        .thinking(ExtractionTags.of(new Think("<think>", "</think>")))
                        .thinking((ExtractionTags) null)
                        .build()
                        .thinking())
                .isNull();

        assertThat(WatsonxChatRequestParameters.builder()
                        .thinking(ThinkingEffort.LOW)
                        .thinking((ThinkingEffort) null)
                        .build()
                        .thinking())
                .isNull();
    }

    @Test
    void toString_should_include_watsonx_fields() {
        String text = fullyPopulated().build().toString();

        assertThat(text)
                .contains("projectId=project-id")
                .contains("spaceId=space-id")
                .contains("seed=42");
    }
}
