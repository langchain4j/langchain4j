package dev.langchain4j.mcp.registryclient;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.registryclient.model.McpGetServerResponse;
import dev.langchain4j.mcp.registryclient.model.McpRegistryPong;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * The registry's timestamps and its {@code is*} flags used to depend on a Jackson-specific
 * deserializer and on field visibility. Both now live on the model, so they are pinned here rather
 * than only by the IT that talks to the live registry.
 */
class McpRegistryJsonTest {

    @Test
    void should_read_the_utc_timestamps_of_the_official_meta() {
        McpGetServerResponse response = McpRegistryJson.fromJson(
                """
                {"server":{"name":"io.github.example/server","version":"1.0.0"},
                 "_meta":{"io.modelcontextprotocol.registry/official":{
                   "status":"active","is_latest":true,
                   "published_at":"2025-09-29T12:00:00Z","updated_at":"2025-10-17T08:30:15Z"}}}""",
                McpGetServerResponse.class);

        var official = response.getMeta().getOfficial();
        assertThat(official.getPublishedAt()).isEqualTo(LocalDateTime.of(2025, 9, 29, 12, 0, 0));
        assertThat(official.getUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 10, 17, 8, 30, 15));
        assertThat(official.isLatest()).isTrue();
        assertThat(official.getStatus()).isEqualTo("active");
    }

    @Test
    void should_leave_absent_timestamps_null() {
        McpGetServerResponse response = McpRegistryJson.fromJson(
                "{\"server\":{\"name\":\"io.github.example/server\"},"
                        + "\"_meta\":{\"io.modelcontextprotocol.registry/official\":{\"status\":\"active\"}}}",
                McpGetServerResponse.class);

        assertThat(response.getMeta().getOfficial().getPublishedAt()).isNull();
        assertThat(response.getMeta().getOfficial().getUpdatedAt()).isNull();
    }

    @Test
    void should_read_a_pong() {
        assertThat(McpRegistryJson.fromJson("{\"pong\":true}", McpRegistryPong.class)
                        .pong())
                .isTrue();
        assertThat(McpRegistryJson.fromJson("{\"pong\":false}", McpRegistryPong.class)
                        .pong())
                .isFalse();
    }

    @Test
    void should_ignore_properties_the_registry_adds_later() {
        McpGetServerResponse response = McpRegistryJson.fromJson(
                "{\"server\":{\"name\":\"x\"},\"a_brand_new_field\":\"whatever\"}", McpGetServerResponse.class);

        assertThat(response.getServer().getName()).isEqualTo("x");
    }
}
