package dev.langchain4j.model.chat.request;

import static dev.langchain4j.model.chat.request.ToolChoice.AUTO;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultChatRequestParametersTest {

    @Test
    void override_with() {

        // given
        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .modelName("model-1")
                .temperature(0.7)
                .topP(0.8)
                .topK(10)
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .maxOutputTokens(100)
                .stopSequences("stop1", "stop2")
                .toolSpecifications(List.of(
                        ToolSpecification.builder().name("tool1").build(),
                        ToolSpecification.builder().name("tool2").build()))
                .toolChoice(AUTO)
                .timeout(Duration.ofSeconds(30))
                .build();

        ChatRequestParameters override = DefaultChatRequestParameters.builder()
                .modelName("model-2")
                .temperature(0.9)
                .timeout(Duration.ofSeconds(5))
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result.modelName()).isEqualTo("model-2");
        assertThat(result.temperature()).isEqualTo(0.9);
        assertThat(result.topP()).isEqualTo(0.8);
        assertThat(result.topK()).isEqualTo(10);
        assertThat(result.frequencyPenalty()).isEqualTo(0.5);
        assertThat(result.presencePenalty()).isEqualTo(0.3);
        assertThat(result.maxOutputTokens()).isEqualTo(100);
        assertThat(result.stopSequences()).containsExactly("stop1", "stop2");
        assertThat(result.toolSpecifications()).hasSize(2);
        assertThat(result.toolChoice()).isEqualTo(AUTO);
        assertThat(result.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void defaulted_by() {

        // given
        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .modelName("model-1")
                .temperature(0.7)
                .topK(10)
                .presencePenalty(0.3)
                .maxOutputTokens(100)
                .stopSequences("stop1", "stop2")
                .toolSpecifications(List.of(
                        ToolSpecification.builder().name("tool1").build(),
                        ToolSpecification.builder().name("tool2").build()))
                .toolChoice(AUTO)
                .timeout(Duration.ofSeconds(5))
                .build();

        ChatRequestParameters defaultParams = DefaultChatRequestParameters.builder()
                .modelName("model-2")
                .temperature(0.9)
                .topP(0.8)
                .topK(12)
                .timeout(Duration.ofSeconds(30))
                .build();

        // when
        ChatRequestParameters result = original.defaultedBy(defaultParams);

        // then
        assertThat(result.modelName()).isEqualTo("model-1");
        assertThat(result.temperature()).isEqualTo(0.7);
        assertThat(result.topP()).isEqualTo(0.8);
        assertThat(result.topK()).isEqualTo(10);
        assertThat(result.frequencyPenalty()).isNull();
        assertThat(result.presencePenalty()).isEqualTo(0.3);
        assertThat(result.maxOutputTokens()).isEqualTo(100);
        assertThat(result.stopSequences()).containsExactly("stop1", "stop2");
        assertThat(result.toolSpecifications()).hasSize(2);
        assertThat(result.toolChoice()).isEqualTo(AUTO);
        assertThat(result.timeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void timeout_defaults_to_null_and_round_trips_through_builder() {

        ChatRequestParameters defaulted =
                DefaultChatRequestParameters.builder().build();
        assertThat(defaulted.timeout()).isNull();

        Duration explicit = Duration.ofMillis(750);
        ChatRequestParameters configured = DefaultChatRequestParameters.builder()
                .timeout(explicit)
                .build();
        assertThat(configured.timeout()).isEqualTo(explicit);
    }
}
