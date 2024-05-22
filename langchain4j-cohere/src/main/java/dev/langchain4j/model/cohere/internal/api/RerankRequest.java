package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;

import java.util.List;
@Builder
public class RerankRequest {

    private String model;
    private String query;
    private List<String> documents;
}
