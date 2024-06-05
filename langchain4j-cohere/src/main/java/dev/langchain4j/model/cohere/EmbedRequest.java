package dev.langchain4j.model.cohere;

import lombok.Builder;

import java.util.List;

@Builder
class EmbedRequest {

    private List<String> texts;
    private String model;
    private String inputType;
}