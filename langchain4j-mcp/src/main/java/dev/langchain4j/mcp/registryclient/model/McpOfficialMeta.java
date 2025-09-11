package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;

public class McpOfficialMeta {

    private String id;

    @JsonProperty("is_latest")
    private boolean isLatest;

    @JsonProperty("published_at")
    private ZonedDateTime publishedAt;

    @JsonProperty("updated_at")
    private ZonedDateTime updatedAt;

    public String getId() {
        return id;
    }

    public boolean isLatest() {
        return isLatest;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
