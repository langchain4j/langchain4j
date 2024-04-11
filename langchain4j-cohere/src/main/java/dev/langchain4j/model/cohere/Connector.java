package dev.langchain4j.model.cohere;

import lombok.Builder;
import lombok.NonNull;

import java.util.Map;

@Builder
public class Connector {

    @NonNull
    String id;

    String userAccessToken;

    boolean continueOnFailure;

    Map<String, String> options;

}
