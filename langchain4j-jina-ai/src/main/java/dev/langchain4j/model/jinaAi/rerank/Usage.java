package dev.langchain4j.model.jinaAi.rerank;

import lombok.Getter;

@Getter
class Usage {

    private Integer totalTokens;
    private Integer promptTokens;
}
