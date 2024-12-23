package dev.langchain4j.web.search.brave;

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
class BraveWebSearchRequest {

    private final String apiKey;
    private final String query;
    private final Map<String,Object> optionalParams;

    @Builder
    BraveWebSearchRequest(String apiKey,
                          String query,
                          Map<String,Object> optionalParams){
        this.apiKey = apiKey;
        this.query = query;
        this.optionalParams = new HashMap<>(optionalParams);
    }
}
