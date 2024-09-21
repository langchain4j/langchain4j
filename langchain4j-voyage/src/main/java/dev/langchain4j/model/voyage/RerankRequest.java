package dev.langchain4j.model.voyage;

import java.util.List;

class RerankRequest {

    private String query;
    private List<String> documents;
    private String model;
    private Integer topK;
    private Boolean returnDocuments;
    private Boolean truncation;
}
