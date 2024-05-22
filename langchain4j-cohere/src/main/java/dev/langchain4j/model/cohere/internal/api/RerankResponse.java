package dev.langchain4j.model.cohere.internal.api;


import lombok.Getter;

import java.util.List;


@Getter
public class RerankResponse {

    private List<Result> results;
    private Meta meta;
}
