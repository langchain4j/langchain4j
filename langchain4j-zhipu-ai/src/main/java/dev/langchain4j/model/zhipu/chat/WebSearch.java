package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public final class WebSearch {
    private final Boolean enable;
    @SerializedName("search_query")
    private final String searchQuery;

    public WebSearch(WebSearchBuilder builder) {
        this.enable = builder.enable;
        this.searchQuery = builder.searchQuery;
    }

    public static WebSearchBuilder builder() {
        return new WebSearchBuilder();
    }

    public Boolean getEnable() {
        return enable;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof WebSearch
                && equalTo((WebSearch) another);
    }

    private boolean equalTo(WebSearch another) {
        return Objects.equals(enable, another.enable)
                && Objects.equals(searchQuery, another.searchQuery);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(enable);
        h += (h << 5) + Objects.hashCode(searchQuery);
        return h;
    }

    @Override
    public String toString() {
        return "WebSearch{" +
                "enable=" + enable +
                ", searchQuery='" + searchQuery + '\'' +
                '}';
    }

    public static class WebSearchBuilder {
        private Boolean enable;
        private String searchQuery;

        WebSearchBuilder() {
        }

        public WebSearchBuilder enable(Boolean enable) {
            this.enable = enable;
            return this;
        }

        public WebSearchBuilder searchQuery(String searchQuery) {
            this.searchQuery = searchQuery;
            return this;
        }

        public WebSearch build() {
            return new WebSearch(this);
        }
    }
}