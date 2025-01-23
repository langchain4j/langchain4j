package dev.langchain4j.model.chat.request;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.chat.request.ToolChoice.AUTO;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultChatRequestParametersTest {

    @Test
    void test_overrideWith() {

        // given
        DefaultChatRequestParameters original = DefaultChatRequestParameters.builder()
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
                        ToolSpecification.builder().name("tool2").build()
                ))
                .toolChoice(AUTO)
                .build();

        DefaultChatRequestParameters override = DefaultChatRequestParameters.builder()
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
}
