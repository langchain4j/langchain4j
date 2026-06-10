package dev.langchain4j.mcp.client;

import static dev.langchain4j.mcp.client.DefaultMcpClient.OBJECT_MAPPER;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.ICONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpMetadataParsingTest {

    @Test
    void resourceWithMetadataAndIcons() throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "result": {
                            "resources": [
                              {
                                "uri": "file:///project/weather.md",
                                "name": "weather",
                                "description": "Weather docs",
                                "mimeType": "text/markdown",
                                "_meta": {
                                  "example.org/status": "stable"
                                },
                                "icons": [
                                  {
                                    "src": "https://example.org/resource.png",
                                    "mimeType": "image/png",
                                    "sizes": ["64x64"],
                                    "theme": "dark"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """);

        McpResource resource = ResourcesHelper.parseResourceRefs(json).get(0);

        assertThat(resource.metadata().get("example.org/status")).isEqualTo("stable");
        assertThat(resource.metadata()).doesNotContainKey(ICONS);
        assertThat(resource.icons())
                .containsExactly(new McpIcon(
                        "image/png", List.of("64x64"), "https://example.org/resource.png", McpIconTheme.DARK));
    }

    @Test
    void resourceTemplateWithMetadataAndIcons() throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "result": {
                            "resourceTemplates": [
                              {
                                "uriTemplate": "file:///project/{name}.md",
                                "name": "docs",
                                "description": "Project docs",
                                "mimeType": "text/markdown",
                                "_meta": {
                                  "example.org/status": "stable"
                                },
                                "icons": [
                                  {
                                    "src": "https://example.org/template.png",
                                    "mimeType": "image/png",
                                    "sizes": ["32x32"],
                                    "theme": "light"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """);

        McpResourceTemplate resourceTemplate =
                ResourcesHelper.parseResourceTemplateRefs(json).get(0);

        assertThat(resourceTemplate.metadata().get("example.org/status")).isEqualTo("stable");
        assertThat(resourceTemplate.metadata()).doesNotContainKey(ICONS);
        assertThat(resourceTemplate.icons())
                .containsExactly(new McpIcon(
                        "image/png", List.of("32x32"), "https://example.org/template.png", McpIconTheme.LIGHT));
    }

    @Test
    void promptWithMetadataAndIcons() throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "result": {
                            "prompts": [
                              {
                                "name": "summarize",
                                "description": "Summarize a document",
                                "arguments": [
                                  {
                                    "name": "document",
                                    "description": "Document text",
                                    "required": true
                                  }
                                ],
                                "_meta": {
                                  "example.org/status": "stable"
                                },
                                "icons": [
                                  {
                                    "src": "https://example.org/prompt.png",
                                    "mimeType": "image/png"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """);

        McpPrompt prompt = PromptsHelper.parsePromptRefs(json).get(0);

        assertThat(prompt.metadata().get("example.org/status")).isEqualTo("stable");
        assertThat(prompt.metadata()).doesNotContainKey(ICONS);
        assertThat(prompt.icons())
                .containsExactly(new McpIcon("image/png", List.of(), "https://example.org/prompt.png", null));
    }

    @Test
    void metadataIsEmptyWhenMetaAndIconsAreAbsent() throws Exception {
        JsonNode resourcesJson = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "result": {
                            "resources": [
                              {
                                "uri": "file:///project/weather.md",
                                "name": "weather"
                              }
                            ]
                          }
                        }
                        """);
        JsonNode resourceTemplatesJson = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "result": {
                            "resourceTemplates": [
                              {
                                "uriTemplate": "file:///project/{name}.md",
                                "name": "docs"
                              }
                            ]
                          }
                        }
                        """);
        JsonNode promptsJson = OBJECT_MAPPER.readTree(
                // language=json
                """
                        {
                          "result": {
                            "prompts": [
                              {
                                "name": "summarize"
                              }
                            ]
                          }
                        }
                        """);

        McpResource resource = ResourcesHelper.parseResourceRefs(resourcesJson).get(0);
        McpResourceTemplate resourceTemplate =
                ResourcesHelper.parseResourceTemplateRefs(resourceTemplatesJson).get(0);
        McpPrompt prompt = PromptsHelper.parsePromptRefs(promptsJson).get(0);

        assertThat(resource.metadata()).isEmpty();
        assertThat(resource.icons()).isEmpty();
        assertThat(resourceTemplate.metadata()).isEmpty();
        assertThat(resourceTemplate.icons()).isEmpty();
        assertThat(prompt.metadata()).isEmpty();
        assertThat(prompt.icons()).isEmpty();
    }
}
