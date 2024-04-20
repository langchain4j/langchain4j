package dev.langchain4j.model.jinaAi;

import lombok.Builder;

import java.util.List;
@Builder
public class EmbeddingRequest {
    String model;
    List<String> texts;
}
