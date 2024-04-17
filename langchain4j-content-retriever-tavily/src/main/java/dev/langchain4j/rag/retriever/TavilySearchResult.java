package dev.langchain4j.rag.retriever;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TavilySearchResult {

    private String title;
    private String url;
    private String content;
    private String rawContent;
    private double score;

}
