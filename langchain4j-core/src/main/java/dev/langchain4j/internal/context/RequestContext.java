package dev.langchain4j.internal.context;

import java.util.Map;

public record RequestContext(Map<String, String> extraHeaders, Map<String, String> extraQueryParams) {
    public static final RequestContext EMPTY = new RequestContext(Map.of(), Map.of());
}
