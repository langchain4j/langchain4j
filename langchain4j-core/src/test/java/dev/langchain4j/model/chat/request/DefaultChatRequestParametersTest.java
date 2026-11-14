package dev.langchain4j.model.chat.request;

import static dev.langchain4j.model.chat.request.ToolChoice.AUTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import dev.langchain4j.model.HttpParameters;

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
    void override_with_should_preserve_provider_specific_parameters() {

        // given
        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .modelName("model-1")
                .temperature(0.7)
                .build();

        ChatRequestParameters override = CustomChatRequestParameters.builder()
                .temperature(0.9)
                .customParameter("custom-value")
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result).isInstanceOf(CustomChatRequestParameters.class);
        assertThat(((CustomChatRequestParameters) result).customParameter()).isEqualTo("custom-value");
        assertThat(result.modelName()).isEqualTo("model-1");
        assertThat(result.temperature()).isEqualTo(0.9);
    }

    @Test
    void defaulted_by_should_preserve_provider_specific_parameters() {

        // given
        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .modelName("model-1")
                .temperature(0.7)
                .build();

        ChatRequestParameters defaultParams = CustomChatRequestParameters.builder()
                .temperature(0.9)
                .topP(0.8)
                .customParameter("custom-value")
                .build();

        // when
        ChatRequestParameters result = original.defaultedBy(defaultParams);

        // then
        assertThat(result).isInstanceOf(CustomChatRequestParameters.class);
        assertThat(((CustomChatRequestParameters) result).customParameter()).isEqualTo("custom-value");
        assertThat(result.modelName()).isEqualTo("model-1");
        assertThat(result.temperature()).isEqualTo(0.7);
        assertThat(result.topP()).isEqualTo(0.8);
    }

    @Test
    void override_with_should_merge_http_parameters() {

        // given
        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Tenant-Id", "tenant-1", "X-Correlation-Id", "old"))
                        .build())
                .build();

        ChatRequestParameters override = DefaultChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("x-correlation-id", "new"))
                        .build())
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result.httpParameters().customHeaders())
                .containsOnly(entry("X-Tenant-Id", "tenant-1"), entry("x-correlation-id", "new"));
    }

    @Test
    void override_with_should_keep_http_parameters_when_override_has_none() {

        HttpParameters httpParameters =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();
        ChatRequestParameters original =
                DefaultChatRequestParameters.builder().httpParameters(httpParameters).build();

        ChatRequestParameters result = original.overrideWith(
                DefaultChatRequestParameters.builder().temperature(0.5).build());

        assertThat(result.httpParameters()).isEqualTo(httpParameters);
        assertThat(result.temperature()).isEqualTo(0.5);
    }

    @Test
    void override_with_should_take_http_parameters_when_original_has_none() {

        HttpParameters httpParameters =
                HttpParameters.builder().customHeaders(Map.of("X-A", "1")).build();

        ChatRequestParameters result = DefaultChatRequestParameters.builder()
                .build()
                .overrideWith(DefaultChatRequestParameters.builder()
                        .httpParameters(httpParameters)
                        .build());

        assertThat(result.httpParameters()).isEqualTo(httpParameters);
    }

    @Test
    void defaulted_by_should_merge_http_parameters_with_current_winning() {

        ChatRequestParameters original = DefaultChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "current"))
                        .build())
                .build();

        ChatRequestParameters defaults = DefaultChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "default", "X-Tenant-Id", "tenant-1"))
                        .build())
                .build();

        ChatRequestParameters result = original.defaultedBy(defaults);

        assertThat(result.httpParameters().customHeaders())
                .containsOnly(entry("X-Correlation-Id", "current"), entry("X-Tenant-Id", "tenant-1"));
    }

    @Test
    void provider_specific_parameters_should_keep_http_parameters_when_merged() {

        ChatRequestParameters defaults = CustomChatRequestParameters.builder()
                .customParameter("custom")
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Tenant-Id", "tenant-1"))
                        .build())
                .build();

        ChatRequestParameters request = DefaultChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "123"))
                        .build())
                .build();

        ChatRequestParameters result = defaults.overrideWith(request);

        assertThat(result).isInstanceOf(CustomChatRequestParameters.class);
        assertThat(((CustomChatRequestParameters) result).customParameter()).isEqualTo("custom");
        assertThat(result.httpParameters().customHeaders())
                .containsOnly(entry("X-Tenant-Id", "tenant-1"), entry("X-Correlation-Id", "123"));
    }

    @Test
    void http_parameters_should_be_null_by_default() {
        assertThat(DefaultChatRequestParameters.builder().build().httpParameters())
                .isNull();
    }
}
