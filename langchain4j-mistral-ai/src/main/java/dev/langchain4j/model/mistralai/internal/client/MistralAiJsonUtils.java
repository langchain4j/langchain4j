package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Internal
class MistralAiJsonUtils {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .prettyPrint(true)
            .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String json, Class<T> clazz) {
        return CODEC.fromJson(json, clazz);
    }

    /**
     * Returns the provider's error message if the given JSON has a top-level {@code error}, otherwise {@code null}.
     * Some OpenAI-compatible providers return such errors with HTTP 200, either as a plain body or as an SSE event.
     * When the error object carries a {@code type} and/or a {@code code} alongside the message,
     * they are appended to the returned detail, so the caller gets the full provider context.
     */
    static String providerErrorMessageFrom(String json) {
        if (isNullOrBlank(json) || !json.contains("\"error\"")) {
            return null;
        }
        Object error;
        try {
            error = fromJson(json, Map.class).get("error");
        } catch (Exception ignored) {
            return null;
        }
        if (error instanceof Map<?, ?> errorMap
                && errorMap.get("message") instanceof String message
                && isNotNullOrBlank(message)) {
            String details = errorDetails(errorMap);
            return details.isEmpty() ? message : message + " (" + details + ")";
        }
        if (error instanceof String message && isNotNullOrBlank(message)) {
            return message;
        }
        return error != null ? json.strip() : null;
    }

    private static String errorDetails(Map<?, ?> error) {
        List<String> details = new ArrayList<>(2);
        if (error.get("type") instanceof String type && isNotNullOrBlank(type)) {
            details.add("type: " + type);
        }
        if (error.get("code") != null) {
            String code =
                    error.get("code") instanceof String stringCode ? stringCode : String.valueOf(error.get("code"));
            if (isNotNullOrBlank(code)) {
                details.add("code: " + code);
            }
        }
        return String.join(", ", details);
    }
}
