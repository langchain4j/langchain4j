package dev.langchain4j.web.search.tavily;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TavilySearchResult {

    private String title;
    private String url;
    private String content;
    private String rawContent;
    private Double score;
}
