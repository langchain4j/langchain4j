package dev.langchain4j.web.search.searchapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

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

    public SearchApiWebSearchResponse() {
    }

    public List<OrganicResult> getOrganicResults() {
        return this.organicResults;
    }

    public Map<String, Object> getSearchParameters() {
        return this.searchParameters;
    }

    public Map<String, Object> getPagination() {
        return this.pagination;
    }

    public Map<String, Object> getSearchInformation() {
        return this.searchInformation;
    }

    public Map<String, Object> getSearchMetadata() {
        return this.searchMetadata;
    }
}

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

    public OrganicResult() {
    }

    public String getTitle() {
        return this.title;
    }

    public String getLink() {
        return this.link;
    }

    public String getPosition() {
        return this.position;
    }

    public String getSnippet() {
        return this.snippet;
    }
}
