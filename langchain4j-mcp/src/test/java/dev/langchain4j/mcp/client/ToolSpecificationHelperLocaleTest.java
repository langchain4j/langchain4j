package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.transport.McpJson;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
class ToolSpecificationHelperLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ"})
    void toolWithDuplicateCaseInsensitiveMcpHeaderIsExcludedInAnyLocale(String languageTag) {
        String text = """
                [{
                    "name": "bad_tool",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "first": {
                          "type": "string",
                          "x-mcp-header": "ID"
                        },
                        "second": {
                          "type": "string",
                          "x-mcp-header": "id"
                        }
                      }
                    }
                }]
                """;

        withDefaultLocale(languageTag, () -> {
            List<Map<String, Object>> json = McpJson.deserialize(McpJson.parse(text), List.class);
            assertThat(ToolSpecificationHelper.toolSpecificationListFromMcpResponse(json))
                    .isEmpty();
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
