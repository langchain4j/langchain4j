package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

class SearchApiJsonUtils {

    private SearchApiJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = WireJson.codec(
            WireJsonSpec.builder().propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE).build());

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
