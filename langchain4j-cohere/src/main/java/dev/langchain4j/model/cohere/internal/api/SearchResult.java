package dev.langchain4j.model.cohere.internal.api;

import lombok.Getter;

import java.util.List;


@Getter
public class SearchResult {

    SearchQuery searchQuery;

    Connector connector;

    List<String> documentIds;

    String errorMessage;

    Boolean continueOnFailure;

}
