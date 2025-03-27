package dev.langchain4j.model.jina.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class JinaRerankingRequest {

    public String model;
    public String query;
    public List<String> documents;
    public Boolean returnDocuments;

    JinaRerankingRequest(String model, String query, List<String> documents, Boolean returnDocuments) {
        this.model = model;
        this.query = query;
        this.documents = documents;
        this.returnDocuments = returnDocuments;
    }

    public static JinaRerankingRequestBuilder builder() {
        return new JinaRerankingRequestBuilder();
    }

    public static class JinaRerankingRequestBuilder {
        private String model;
        private String query;
        private List<String> documents;
        private Boolean returnDocuments;

        JinaRerankingRequestBuilder() {
        }

        public JinaRerankingRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public JinaRerankingRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        public JinaRerankingRequestBuilder documents(List<String> documents) {
            this.documents = documents;
            return this;
        }

        public JinaRerankingRequestBuilder returnDocuments(Boolean returnDocuments) {
            this.returnDocuments = returnDocuments;
            return this;
        }

        public JinaRerankingRequest build() {
            return new JinaRerankingRequest(this.model, this.query, this.documents, this.returnDocuments);
        }

        public String toString() {
            return "JinaRerankingRequest.JinaRerankingRequestBuilder(model=" + this.model + ", query=" + this.query + ", documents=" + this.documents + ", returnDocuments=" + this.returnDocuments + ")";
        }
    }
}
