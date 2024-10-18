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
class BulkRequest {

    private List<Map<String, Object>> documents;

    BulkRequest() {
    }

    BulkRequest(List<Map<String, Object>> documents) {
        this.documents = documents;
    }

    public List<Map<String, Object>> getDocuments() {
        return documents;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private List<Map<String, Object>> documents;

        Builder documents(List<Map<String, Object>> documents) {
            this.documents = documents;
            return this;
        }

        BulkRequest build() {
            return new BulkRequest(documents);
        }
    }
}
