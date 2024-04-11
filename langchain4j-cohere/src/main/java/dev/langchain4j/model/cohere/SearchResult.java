package dev.langchain4j.model.cohere;

import lombok.Getter;

import java.util.List;

@Getter
public class SearchResult {

    SearchQuery searchQuery;

    Connector connector;

    List<String> documentIds;

    String errorMessage;

    boolean continueOnFailure;

}
