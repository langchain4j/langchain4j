package dev.langchain4j.model.tei.client;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class EmbeddingResponse {

    private String model;

    private List<Embedding> data;

    private Usage usage;
}
