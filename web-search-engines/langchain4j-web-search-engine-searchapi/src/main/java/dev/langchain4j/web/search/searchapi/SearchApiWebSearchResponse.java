package dev.langchain4j.web.search.searchapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SearchApiWebSearchResponse {

    private List<OrganicResult> organicResults;

    /**
     * Always present in a successful response
     */
    private Map<String, Object> searchParameters;

    /**
     * May or may not be present
     */
    private Map<String, Object> pagination;

    /**
     * May or may not be present
     */
    private Map<String, Object> searchInformation;

    /**
     * May or may not be present
     */
    private Map<String, Object> searchMetadata;
}

@Getter
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class OrganicResult {

    private String title;
    private String link;
    private String position;

    /**
     * May or may not be present
     */
    private String snippet;
}