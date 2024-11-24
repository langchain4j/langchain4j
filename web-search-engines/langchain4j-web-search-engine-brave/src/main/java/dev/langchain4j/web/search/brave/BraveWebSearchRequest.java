package dev.langchain4j.web.search.brave;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BraveWebSearchRequest {

    private String query;
    private String apiKey;
    private Integer count;
    private String safeSearch;
    private String resultFilter;
    private String freshness;

}
