package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
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