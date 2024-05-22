package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;
import lombok.NonNull;

import java.util.Map;

@Builder
public class Connector {

    @NonNull
    String id;

    String userAccessToken;

    Boolean continueOnFailure;

    Map<String, String> options;

}
