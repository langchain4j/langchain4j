package dev.langchain4j.model.cohere.internal.api;

import lombok.Getter;


@Getter
public class Result {

    private Integer index;
    private Double relevanceScore;

}
