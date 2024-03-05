package dev.langchain4j.model.cohere;

import lombok.Builder;

import java.util.List;

@Builder
class RerankRequest {

    private String model;
    private String query;
    private List<String> documents;
}
