package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;
import lombok.NonNull;

@Builder
public class ChatHistory {

    @NonNull
    CohereRole role;

    @NonNull
    String message;

}
