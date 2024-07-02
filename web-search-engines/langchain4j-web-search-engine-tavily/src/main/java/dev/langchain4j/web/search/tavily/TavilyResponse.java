package dev.langchain4j.web.search.tavily;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
class TavilyResponse {

    private String answer;
    private String query;
    private Double responseTime;
    private List<String> images;
    private List<String> followUpQuestions;
    private List<TavilySearchResult> results;
}
