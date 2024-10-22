package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpsertRequest {

    private String dbName;
    private String spaceName;
    private List<Map<String, Object>> documents;

    UpsertRequest() {
    }

    UpsertRequest(String dbName, String spaceName, List<Map<String, Object>> documents) {
        this.dbName = dbName;
        this.spaceName = spaceName;
        this.documents = documents;
    }

    public String getDbName() {
        return dbName;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public List<Map<String, Object>> getDocuments() {
        return documents;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String dbName;
        private String spaceName;
        private List<Map<String, Object>> documents;

        Builder dbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        Builder spaceName(String spaceName) {
            this.spaceName = spaceName;
            return this;
        }

        Builder documents(List<Map<String, Object>> documents) {
            this.documents = documents;
            return this;
        }

        UpsertRequest build() {
            return new UpsertRequest(dbName, spaceName, documents);
        }
    }
}
