package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.LocalDateTime;

public class McpOfficialMeta {

    @JsonAlias("id")
    private String serverId;

    @JsonAlias("is_latest")
    private boolean isLatest;

    @JsonAlias("published_at")
    private LocalDateTime publishedAt;

    @JsonAlias("updated_at")
    private LocalDateTime updatedAt;

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

    /**
     * The date and time when the server was published.
     * It is evaluated in the UTC.
     */
    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    /**
     * The date and time when the server was last updated.
     * It is evaluated in the UTC.
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "McpOfficialMeta{" + "serverId='"
                + serverId + '\'' + ", isLatest="
                + isLatest + ", publishedAt="
                + publishedAt + ", updatedAt="
                + updatedAt + ", status='"
                + status + '\'' + '}';
    }
}
