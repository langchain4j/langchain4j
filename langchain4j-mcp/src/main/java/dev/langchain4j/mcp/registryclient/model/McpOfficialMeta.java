package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.ZonedDateTime;

public class McpOfficialMeta {

    @JsonAlias("id")
    private String serverId;

    @JsonAlias("is_latest")
    private boolean isLatest;

    @JsonAlias("published_at")
    private ZonedDateTime publishedAt;

    @JsonAlias("updated_at")
    private ZonedDateTime updatedAt;

    public String getServerId() {
        return serverId;
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
