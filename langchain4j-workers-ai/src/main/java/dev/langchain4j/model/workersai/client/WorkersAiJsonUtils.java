package dev.langchain4j.model.workersai.client;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

class WorkersAiJsonUtils {

    private WorkersAiJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder().build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
