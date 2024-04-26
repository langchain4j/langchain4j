package dev.langchain4j.rag.retriever;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TavilySearchRequest {

    private String apiKey;
    private String query;
    private SearchDepth searchDepth;
    private Boolean includeImages;
    private Boolean includeAnswer;
    private Boolean includeRawContent;
    private Integer maxResults;
    private List<String> includeDomains;
    private List<String> excludeDomains;

}
