package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;


@Builder
@Getter
public class ToolCall {
    String name;
    Map<String, String> parameters;
}
