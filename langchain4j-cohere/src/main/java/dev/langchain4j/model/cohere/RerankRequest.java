package dev.langchain4j.model.cohere;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
class RerankRequest {

    private String model;
    private String query;
    private List<String> documents;
}
