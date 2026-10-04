package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiPromptCacheOptionsTest {

    @Test
    void should_create_implicit_and_explicit_options() {
        assertThat(OpenAiPromptCacheOptions.implicit().mode()).isEqualTo("implicit");
        assertThat(OpenAiPromptCacheOptions.implicit().ttl()).isNull();
        assertThat(OpenAiPromptCacheOptions.explicit().mode()).isEqualTo("explicit");
        assertThat(OpenAiPromptCacheOptions.explicit().ttl()).isNull();
    }

    @Test
    void should_build_with_ttl() {
        OpenAiPromptCacheOptions options = OpenAiPromptCacheOptions.builder()
                .mode(OpenAiPromptCacheOptions.MODE_EXPLICIT)
                .ttl(OpenAiPromptCacheOptions.TTL_30M)
                .build();

        assertThat(options.mode()).isEqualTo("explicit");
        assertThat(options.ttl()).isEqualTo("30m");
        assertThat(options).hasToString("OpenAiPromptCacheOptions{mode=\"explicit\", ttl=\"30m\"}");
    }

    @Test
    void should_be_empty_when_nothing_is_set() {
        OpenAiPromptCacheOptions options = OpenAiPromptCacheOptions.builder().build();

        assertThat(options.mode()).isNull();
        assertThat(options.ttl()).isNull();
    }

    @Test
    void should_respect_equals_and_hash_code() {
        OpenAiPromptCacheOptions explicit = OpenAiPromptCacheOptions.explicit();

        assertThat(explicit)
                .isEqualTo(explicit)
                .isEqualTo(OpenAiPromptCacheOptions.explicit())
                .hasSameHashCodeAs(OpenAiPromptCacheOptions.explicit())
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isNotEqualTo(OpenAiPromptCacheOptions.implicit())
                .isNotEqualTo(OpenAiPromptCacheOptions.builder()
                        .mode(OpenAiPromptCacheOptions.MODE_EXPLICIT)
                        .ttl(OpenAiPromptCacheOptions.TTL_30M)
                        .build());
    }

    @Test
    void should_not_be_marked_when_attributes_are_null_or_do_not_contain_the_key() {
        assertThat(OpenAiPromptCacheBreakpoint.isMarked(null)).isFalse();
        assertThat(OpenAiPromptCacheBreakpoint.isMarked(Map.of())).isFalse();
        assertThat(OpenAiPromptCacheBreakpoint.isMarked(Map.of("other", "value")))
                .isFalse();

        Map<String, Object> attributesWithNullValue = new HashMap<>();
        attributesWithNullValue.put(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, null);
        assertThat(OpenAiPromptCacheBreakpoint.isMarked(attributesWithNullValue))
                .isFalse();
    }

    @Test
    void should_be_marked_when_mode_is_explicit() {
        assertThat(OpenAiPromptCacheBreakpoint.isMarked(
                        Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT)))
                .isTrue();
    }

    @Test
    void should_fail_when_mode_is_not_supported() {
        assertThatThrownBy(() -> OpenAiPromptCacheBreakpoint.isMarked(
                        Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, "implicit")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported value for the \"prompt_cache_breakpoint\" attribute: implicit. "
                        + "The only supported value is \"explicit\".");

        assertThatThrownBy(() ->
                        OpenAiPromptCacheBreakpoint.isMarked(Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, true)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("true");
    }
}
