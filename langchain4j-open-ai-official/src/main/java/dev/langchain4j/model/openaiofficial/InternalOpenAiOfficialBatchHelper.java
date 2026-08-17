package dev.langchain4j.model.openaiofficial;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.core.ObjectMappers;
import com.openai.models.batches.Batch;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

class InternalOpenAiOfficialBatchHelper {

    static final String CUSTOM_ID_PREFIX = "request-";

    private static final String CHAT_COMPLETIONS_URL = "/v1/chat/completions";
    private static final String POST_METHOD = "POST";
    private static final String UNKNOWN_ERROR_MESSAGE = "unknown";
    private static final int HTTP_OK = 200;
    private static final int NO_STATUS_CODE = 0;

    private static final JsonMapper JSON_MAPPER = ObjectMappers.jsonMapper();

    private InternalOpenAiOfficialBatchHelper() {}

    static String toCustomId(int requestIndex) {
        return CUSTOM_ID_PREFIX + requestIndex;
    }

    private static int toRequestIndex(@Nullable String customId) {
        if (customId == null || !customId.startsWith(CUSTOM_ID_PREFIX)) {
            throw invalidCustomId(customId, null);
        }
        try {
            int requestIndex = Integer.parseInt(customId.substring(CUSTOM_ID_PREFIX.length()));
            if (requestIndex < 0) {
                throw invalidCustomId(customId, null);
            }
            return requestIndex;
        } catch (NumberFormatException e) {
            throw invalidCustomId(customId, e);
        }
    }

    private static IllegalStateException invalidCustomId(@Nullable String customId, @Nullable Throwable cause) {
        return new IllegalStateException("Unexpected custom_id in batch result: " + customId, cause);
    }

    static byte[] toJsonl(List<ChatCompletionCreateParams> requests) {
        StringBuilder jsonl = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            ObjectNode line = JSON_MAPPER.createObjectNode();
            line.put("custom_id", toCustomId(i));
            line.put("method", POST_METHOD);
            line.put("url", CHAT_COMPLETIONS_URL);
            line.set("body", JSON_MAPPER.valueToTree(requests.get(i)._body()));
            jsonl.append(line).append('\n');
        }
        return jsonl.toString().getBytes(UTF_8);
    }

    static BatchState toBatchState(Batch.Status status) {
        return switch (status.value()) {
            case VALIDATING -> BatchState.PENDING;
            case IN_PROGRESS, FINALIZING, CANCELLING -> BatchState.RUNNING;
            case COMPLETED -> BatchState.SUCCEEDED;
            case FAILED -> BatchState.FAILED;
            case CANCELLED -> BatchState.CANCELLED;
            case EXPIRED -> BatchState.EXPIRED;
            case _UNKNOWN -> BatchState.UNSPECIFIED;
        };
    }

    static List<ResultLine> toResultLines(InputStream content) {
        List<ResultLine> resultLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(content, UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    resultLines.add(toResultLine(JSON_MAPPER.readTree(line)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read batch results", e);
        }
        return resultLines;
    }

    static BatchError toBatchError(com.openai.models.batches.BatchError error) {
        Map<String, Object> details = new LinkedHashMap<>();
        error.code().ifPresent(code -> details.put("code", code));
        error.param().ifPresent(param -> details.put("param", param));
        error.line().ifPresent(line -> details.put("line", line));
        return new BatchError(NO_STATUS_CODE, error.message().orElse(UNKNOWN_ERROR_MESSAGE), toDetails(details));
    }

    private static ResultLine toResultLine(JsonNode node) throws JsonProcessingException {
        int requestIndex = toRequestIndex(text(node, "custom_id"));

        JsonNode error = node.get("error");
        if (isPresent(error)) {
            return ResultLine.failure(requestIndex, toBatchError(NO_STATUS_CODE, error));
        }

        JsonNode response = node.get("response");
        if (!isPresent(response)) {
            return ResultLine.failure(requestIndex, unknownError(NO_STATUS_CODE));
        }

        int statusCode = response.path("status_code").asInt();
        JsonNode body = response.get("body");
        if (statusCode == HTTP_OK && isPresent(body)) {
            return ResultLine.success(requestIndex, JSON_MAPPER.treeToValue(body, ChatCompletion.class));
        }

        return ResultLine.failure(requestIndex, toResponseError(statusCode, body));
    }

    private static BatchError toResponseError(int statusCode, @Nullable JsonNode body) {
        JsonNode bodyError = isPresent(body) ? body.get("error") : null;
        return isPresent(bodyError) ? toBatchError(statusCode, bodyError) : unknownError(statusCode);
    }

    private static BatchError unknownError(int statusCode) {
        return new BatchError(statusCode, UNKNOWN_ERROR_MESSAGE, null);
    }

    private static BatchError toBatchError(int code, JsonNode error) {
        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "type", text(error, "type"));
        putIfPresent(details, "code", text(error, "code"));
        putIfPresent(details, "param", text(error, "param"));
        String message = text(error, "message");
        return new BatchError(code, message != null ? message : UNKNOWN_ERROR_MESSAGE, toDetails(details));
    }

    private static @Nullable List<Map<String, Object>> toDetails(Map<String, Object> details) {
        return details.isEmpty() ? null : List.of(details);
    }

    private static void putIfPresent(Map<String, Object> details, String key, @Nullable String value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private static @Nullable String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return isPresent(value) && value.isValueNode() ? value.asText() : null;
    }

    private static boolean isPresent(@Nullable JsonNode node) {
        return node != null && !node.isNull();
    }

    record ResultLine(
            int requestIndex,
            @Nullable ChatCompletion completion,
            @Nullable BatchError error) {

        static ResultLine success(int requestIndex, ChatCompletion completion) {
            return new ResultLine(requestIndex, completion, null);
        }

        static ResultLine failure(int requestIndex, BatchError error) {
            return new ResultLine(requestIndex, null, error);
        }
    }
}
