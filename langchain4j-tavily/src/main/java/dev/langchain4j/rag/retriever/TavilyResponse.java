package dev.langchain4j.rag.retriever;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class TavilyResponse {

    private String answer;
    private String query;
    private String responseTime;
    private List<String> images;
    private List<String> followUpQuestions;
    private List<TavilySearchResult> results;

}
