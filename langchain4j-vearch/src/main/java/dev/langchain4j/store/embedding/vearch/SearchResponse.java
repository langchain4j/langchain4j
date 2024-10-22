package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class SearchResponse {

    private List<List<Map<String, Object>>> documents;

    SearchResponse() {
    }

    SearchResponse(List<List<Map<String, Object>>> documents) {
        this.documents = documents;
    }

    public List<List<Map<String, Object>>> getDocuments() {
        return documents;
    }

    public void setDocuments(List<List<Map<String, Object>>> documents) {
        this.documents = documents;
    }
}
