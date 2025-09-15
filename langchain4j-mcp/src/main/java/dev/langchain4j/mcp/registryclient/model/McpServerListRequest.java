package dev.langchain4j.mcp.registryclient.model;

import java.time.ZonedDateTime;

public class McpServerListRequest {

    private final String cursor;
    private final Long limit;
    private final String search;
    private final ZonedDateTime updatedSince;
    private final String version;

    private McpServerListRequest(String cursor, Long limit, String search, ZonedDateTime updatedSince, String version) {
        this.cursor = cursor;
        this.limit = limit;
        this.search = search;
        this.updatedSince = updatedSince;
        this.version = version;
    }

    public String getCursor() {
        return cursor;
    }

    public Long getLimit() {
        return limit;
    }

    public String getSearch() {
        return search;
    }

    public ZonedDateTime getUpdatedSince() {
        return updatedSince;
    }

    public String getVersion() {
        return version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String cursor;
        private Long limit;
        private String search;
        private ZonedDateTime updatedSince;
        private String version;

        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        public Builder limit(Long limit) {
            this.limit = limit;
            return this;
        }

        public Builder search(String search) {
            this.search = search;
            return this;
        }

        public Builder updatedSince(ZonedDateTime updatedSince) {
            this.updatedSince = updatedSince;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public McpServerListRequest build() {
            return new McpServerListRequest(cursor, limit, search, updatedSince, version);
        }
    }
}
