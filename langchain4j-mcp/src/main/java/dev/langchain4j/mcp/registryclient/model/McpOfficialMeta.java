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

    private String status;

    /**
     * @deprecated This field was removed in the 2025-09-29 version of the schema.
     */
    @Deprecated(forRemoval = true)
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

    public String getStatus() {
        return status;
    }
}
