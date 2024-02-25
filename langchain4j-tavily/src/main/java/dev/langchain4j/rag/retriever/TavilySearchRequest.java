package dev.langchain4j.rag.retriever;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TavilySearchRequest {

    private String apiKey;
    private String query;
    private String searchDepth;
    private Boolean includeImages;
    private Boolean includeAnswer;
    private Boolean includeRawContent;
    private Integer maxResults;
    private String[] includeDomains;
    private String[] excludeDomains;

}
