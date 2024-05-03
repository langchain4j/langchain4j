package dev.langchain4j.model.jinaAi.rerank;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Getter
class RerankResponse {

    private String model;
    private Usage usage;
    private List<Result> results;
}
