package dev.langchain4j.model.cohere;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
class EmbedRequest {

    private List<String> texts;
    private String model;
    private String inputType;
}