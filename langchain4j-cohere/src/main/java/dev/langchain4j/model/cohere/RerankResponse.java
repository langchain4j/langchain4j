package dev.langchain4j.model.cohere;

import lombok.Getter;

import java.util.List;

@Getter
class RerankResponse {

    private List<Result> results;
    private Meta meta;
}
