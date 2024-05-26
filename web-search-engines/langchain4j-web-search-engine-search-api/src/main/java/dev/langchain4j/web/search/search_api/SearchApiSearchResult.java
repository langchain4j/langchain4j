package dev.langchain4j.web.search.search_api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class SearchApiSearchResult {

    private String title;
    private String url;
    private String content;
    private String rawContent;
    private Double score;
}
