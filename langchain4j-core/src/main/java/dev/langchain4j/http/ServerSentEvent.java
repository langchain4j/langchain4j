package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Experimental
public class ServerSentEvent {

    private final String type;
    private final String data;
}
