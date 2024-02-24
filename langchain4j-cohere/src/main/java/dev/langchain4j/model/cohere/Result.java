package dev.langchain4j.model.cohere;

import lombok.Getter;

@Getter
class Result {

    private Integer index;
    private Double relevanceScore;
}
