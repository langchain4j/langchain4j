package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

class SearchApiJsonUtils {

    private SearchApiJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = ProviderJson.codec(
            ProviderJsonSpec.builder().propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE).build());

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
