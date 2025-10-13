package dev.langchain4j.mcp.registryclient.model;

import java.time.LocalDateTime;

public class McpServerListRequest {

    private final String cursor;
    private final Long limit;
    private final String search;
    private final LocalDateTime updatedSince;
    private final String version;

    private McpServerListRequest(String cursor, Long limit, String search, LocalDateTime updatedSince, String version) {
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

    public LocalDateTime getUpdatedSince() {
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
        private LocalDateTime updatedSince;
        private String version;

        /**
         * Pagination cursor.
         */
        public Builder cursor(String cursor) {
            this.cursor = cursor;
            return this;
        }

        /**
         * Number of items per page. The default is 30.
         */
        public Builder limit(Long limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Search servers by name (substring match).
         */
        public Builder search(String search) {
            this.search = search;
            return this;
        }

        /**
         * Only return servers updated since this date.
         * The date and time should be in the UTC.
         */
        public Builder updatedSince(LocalDateTime updatedSince) {
            this.updatedSince = updatedSince;
            return this;
        }

        /**
         * Filter by version ('latest' for latest version, or an exact version like '1.2.3').
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public McpServerListRequest build() {
            return new McpServerListRequest(cursor, limit, search, updatedSince, version);
        }
    }

    @Override
    public String toString() {
        return "McpServerListRequest{" + "cursor='"
                + cursor + '\'' + ", limit="
                + limit + ", search='"
                + search + '\'' + ", updatedSince="
                + updatedSince + ", version='"
                + version + '\'' + '}';
    }
}
