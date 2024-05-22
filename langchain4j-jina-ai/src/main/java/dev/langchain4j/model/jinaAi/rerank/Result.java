package dev.langchain4j.model.jinaAi.rerank;

import lombok.Getter;

@Getter
class Result {

    private Integer index;
    private Document document;
    private Double relevanceScore;
}
