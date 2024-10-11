package dev.langchain4j.model.voyageai;

import java.util.List;

class RerankRequest {

    private String query;
    private List<String> documents;
    private String model;
    private Integer topK;
    private Boolean returnDocuments;
    private Boolean truncation;

    RerankRequest() {
    }

    RerankRequest(String query, List<String> documents, String model, Integer topK, Boolean returnDocuments, Boolean truncation) {
        this.query = query;
        this.documents = documents;
        this.model = model;
        this.topK = topK;
        this.returnDocuments = returnDocuments;
        this.truncation = truncation;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public String getModel() {
        return model;
    }

    public Integer getTopK() {
        return topK;
    }

    public Boolean getReturnDocuments() {
        return returnDocuments;
    }

    public Boolean getTruncation() {
        return truncation;
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private String query;
        private List<String> documents;
        private String model;
        private Integer topK;
        private Boolean returnDocuments;
        private Boolean truncation;

        Builder query(String query) {
            this.query = query;
            return this;
        }

        Builder documents(List<String> documents) {
            this.documents = documents;
            return this;
        }

        Builder model(String model) {
            this.model = model;
            return this;
        }

        Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        Builder returnDocuments(Boolean returnDocuments) {
            this.returnDocuments = returnDocuments;
            return this;
        }

        Builder truncation(Boolean truncation) {
            this.truncation = truncation;
            return this;
        }

        RerankRequest build() {
            return new RerankRequest(query, documents, model, topK, returnDocuments, truncation);
        }
    }
}
