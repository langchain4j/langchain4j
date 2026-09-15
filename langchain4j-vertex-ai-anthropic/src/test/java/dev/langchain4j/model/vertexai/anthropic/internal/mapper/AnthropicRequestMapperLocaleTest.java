package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicTool;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class AnthropicRequestMapperLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_derive_default_description_independently_of_default_locale(String languageTag) {
        withDefaultLocale(languageTag, () -> {
            AnthropicTool tool = AnthropicRequestMapper.toAnthropicTool(
                    ToolSpecification.builder().name("TIME").build());

            assertThat(tool.description).isEqualTo("Gets the current time");
        });
    }

    private static void withDefaultLocale(String languageTag, Runnable action) {
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            action.run();
        } finally {
            Locale.setDefault(previousDefault);
        }
    }
}
