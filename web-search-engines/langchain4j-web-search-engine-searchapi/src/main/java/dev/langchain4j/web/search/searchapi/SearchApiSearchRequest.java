package dev.langchain4j.web.search.searchapi;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
class SearchApiSearchRequest {

    private String apiKey;
    private String query;
    private String searchDepth;
    private Boolean includeAnswer;
    private Boolean includeRawContent;
    private Integer maxResults;
    private List<String> includeDomains;
    private List<String> excludeDomains;
}
