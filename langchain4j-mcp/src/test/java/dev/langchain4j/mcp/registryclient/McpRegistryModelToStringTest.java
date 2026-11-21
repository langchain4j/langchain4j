package dev.langchain4j.mcp.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.registryclient.model.McpEnvironmentVariable;
import org.junit.jupiter.api.Test;

class McpRegistryModelToStringTest {

    @Test
    void environment_variable_toString_should_redact_secret_values() {
        McpEnvironmentVariable variable = McpRegistryJson.fromJson(
                """
                {"name": "API_KEY", "isSecret": true, "value": "secret-value", "default": "secret-default"}""",
                McpEnvironmentVariable.class);

        assertThat(variable.toString())
                .doesNotContain("secret-value")
                .doesNotContain("secret-default")
                .contains("value='<redacted>'")
                .contains("defaultValue='<redacted>'")
                .contains("name='API_KEY'");
    }

    @Test
    void environment_variable_toString_should_show_values_that_are_not_secret() {
        McpEnvironmentVariable variable = McpRegistryJson.fromJson(
                """
                {"name": "LOG_LEVEL", "isSecret": false, "value": "debug", "default": "info"}""",
                McpEnvironmentVariable.class);

        assertThat(variable.toString()).contains("value='debug'").contains("defaultValue='info'");
    }
}
