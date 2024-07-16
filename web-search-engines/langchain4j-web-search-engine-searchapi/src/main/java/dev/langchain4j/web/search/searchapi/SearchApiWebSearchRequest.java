package dev.langchain4j.web.search.searchapi;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
        this.engine = ensureNotNull(engine, "engine");
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.query = ensureNotBlank(query, "query");
        this.finalOptionalParameters = optionalParameters;
        if (additionalRequestParameters != null) {
            finalOptionalParameters.putAll(additionalRequestParameters);
        }
    }
}
