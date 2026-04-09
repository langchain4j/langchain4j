package dev.langchain4j.model.chat.request;

import static dev.langchain4j.model.chat.request.ToolChoice.AUTO;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Map;
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
                .build();

        ChatRequestParameters override = DefaultChatRequestParameters.builder()
                .modelName("model-2")
                .temperature(0.9)
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
                .build();

        ChatRequestParameters defaultParams = DefaultChatRequestParameters.builder()
                .modelName("model-2")
                .temperature(0.9)
                .topP(0.8)
                .topK(12)
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
    }

    @Test
    void customParameters() {

        // given
        Map<String, Object> customParams = Map.of("magic_number", 3.14, "new_feature", true);

        // when
        ChatRequestParameters params = DefaultChatRequestParameters.builder()
                .modelName("model-1")
                .customParameters(customParams)
                .build();

        // then
        assertThat(params.customParameters()).containsEntry("magic_number", 3.14);
        assertThat(params.customParameters()).containsEntry("new_feature", true);
    }

    @Test
    void customParameters_overrideWith() {

        // given
        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .modelName("model-1")
                .customParameters(Map.of("param1", "value1"))
                .build();

        ChatRequestParameters override = DefaultChatRequestParameters.builder()
                .customParameters(Map.of("param2", "value2"))
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result.customParameters()).containsEntry("param2", "value2");
        assertThat(result.customParameters()).doesNotContainKey("param1");
    }
}
