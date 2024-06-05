package dev.langchain4j.web.search.tavily;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class TavilySearchResult {

    private String title;
    private String url;
    private String content;
    private String rawContent;
    private Double score;
}
