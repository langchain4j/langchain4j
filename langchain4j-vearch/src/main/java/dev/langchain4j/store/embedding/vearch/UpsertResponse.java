package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.store.embedding.vearch.VearchConfig.DEFAULT_ID_FIELD_NAME;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class UpsertResponse {

    private Integer total;
    private List<DocumentInfo> documentIds;

    UpsertResponse() {
    }

    UpsertResponse(Integer total, List<DocumentInfo> documentIds) {
        this.total = total;
        this.documentIds = documentIds;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<DocumentInfo> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<DocumentInfo> documentIds) {
        this.documentIds = documentIds;
    }

    static class DocumentInfo {

        @JsonProperty(DEFAULT_ID_FIELD_NAME)
        private String id;

        DocumentInfo() {
        }

        DocumentInfo(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
