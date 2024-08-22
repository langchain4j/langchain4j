package dev.langchain4j.web.search.searchapi;

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
class SearchApiWebSearchRequest {

    private final String engine;
    private final String apiKey;
    private final String query;
    private final Map<String, Object> finalOptionalParameters;

    /**
     * @param additionalRequestParameters overrides optionalParameters for matching keys
     */
    @Builder
    SearchApiWebSearchRequest(String engine,
                              String apiKey,
                              String query,
                              Map<String, Object> optionalParameters,
                              Map<String, Object> additionalRequestParameters) {
        this.engine = engine;
        this.apiKey = apiKey;
        this.query = query;
        this.finalOptionalParameters = new HashMap<>(optionalParameters);
        if (additionalRequestParameters != null) {
            finalOptionalParameters.putAll(additionalRequestParameters);
        }
    }
}
