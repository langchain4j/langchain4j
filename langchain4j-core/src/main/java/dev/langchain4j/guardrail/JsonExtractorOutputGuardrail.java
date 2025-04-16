package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OutputGuardrail} that will check whether or not a response can be successfully deserialized to an object
 * of type {@code T} from JSON
 * <p>
 *     If deserialization fails, the LLM will be reprompted with {@link #getInvalidJsonReprompt(AiMessage, String)}, which
 *     defaults to {@link #DEFAULT_REPROMPT_PROMPT}.
 * </p>
 *
 * @param <T> The type of object that the class should deserialize from JSON
 */
public class JsonExtractorOutputGuardrail<T> implements OutputGuardrail {
    /**
     * The default message to use when reprompting
     */
    public static final String DEFAULT_REPROMPT_MESSAGE = "Invalid JSON";

    /**
     * The default prompt to append to the LLM during a reprompt
     */
    public static final String DEFAULT_REPROMPT_PROMPT =
            "Make sure you return a valid JSON object following the specified format";

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonExtractorOutputGuardrail.class);
    private final ObjectMapper objectMapper;
    private Class<T> outputClass;
    private TypeReference<T> outputType;

    public JsonExtractorOutputGuardrail(ObjectMapper objectMapper, Class<T> outputClass) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
        this.outputClass = ensureNotNull(outputClass, "outputClass");
    }

    public JsonExtractorOutputGuardrail(ObjectMapper objectMapper, TypeReference<T> outputType) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
        this.outputType = ensureNotNull(outputType, "outputType");
    }

    public JsonExtractorOutputGuardrail(Class<T> outputClass) {
        this(new ObjectMapper(), outputClass);
    }

    public JsonExtractorOutputGuardrail(TypeReference<T> outputType) {
        this(new ObjectMapper(), outputType);
    }

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        var llmResponse = ensureNotNull(responseFromLLM, "responseFromLLM").text();
        LOGGER.debug("LLM output: {}", llmResponse);

        return deserialize(llmResponse).map(r -> successWith(llmResponse, r)).orElseGet(() -> {
            LOGGER.debug("LLM output contained invalid JSON. Attempting to trim non-JSON");
            var json = trimNonJson(llmResponse);

            LOGGER.debug("Attempting to deserialize trimmed JSON: {}", json);
            return deserialize(json)
                    .map(r -> successWith(json, r))
                    .orElseGet(() -> invokeInvalidJson(responseFromLLM, json));
        });
    }

    protected String trimNonJson(String llmResponse) {
        var jsonMapStart = llmResponse.indexOf('{');
        var jsonListStart = llmResponse.indexOf('[');

        if ((jsonMapStart < 0) && (jsonListStart < 0)) {
            return "";
        }

        var isJsonMap = (jsonMapStart >= 0) && ((jsonMapStart < jsonListStart) || (jsonListStart < 0));
        var jsonStart = isJsonMap ? jsonMapStart : jsonListStart;
        var jsonEnd = isJsonMap ? llmResponse.lastIndexOf('}') : llmResponse.lastIndexOf(']');

        return (jsonEnd >= 0) && (jsonStart < jsonEnd) ? llmResponse.substring(jsonStart, jsonEnd + 1) : "";
    }

    protected OutputGuardrailResult invokeInvalidJson(AiMessage aiMessage, String json) {
        LOGGER.debug("Found invalid JSON for aiMessage = {} and json = {}", aiMessage, json);
        return reprompt(getInvalidJsonMessage(aiMessage, json), getInvalidJsonReprompt(aiMessage, json));
    }

    /**
     * Generates a message indicating that the provided JSON is invalid.
     *
     * @param aiMessage the AI message associated with the invalid JSON. This parameter is not used.
     * @param json the JSON that failed validation. This parameter is not used.
     * @return a default message indicating that the JSON is invalid.
     */
    protected String getInvalidJsonMessage(
            @SuppressWarnings("unused") AiMessage aiMessage, @SuppressWarnings("unused") String json) {
        return DEFAULT_REPROMPT_MESSAGE;
    }

    /**
     * Generates a reprompt message indicating that the provided JSON is invalid.
     * <p>
     *     This message is appended to the user message from the previous request.
     * </p>
     *
     * @param aiMessage the AI message associated with the invalid JSON. This parameter is not used.
     * @param json the JSON input that failed validation. This parameter is not used.
     * @return a reprompt message indicating that the JSON is invalid.
     */
    protected String getInvalidJsonReprompt(
            @SuppressWarnings("unused") AiMessage aiMessage, @SuppressWarnings("unused") String json) {
        return DEFAULT_REPROMPT_PROMPT;
    }

    /**
     * Tries to deserialize the provided LLM response string into an object of type T using the configured {@link ObjectMapper}.
     * If deserialization fails, an empty Optional is returned.
     *
     * @param llmResponse the JSON-formatted response string to be deserialized
     * @return an Optional containing the deserialized object if successful, or an empty Optional if deserialization fails
     */
    protected Optional<T> deserialize(String llmResponse) {
        try {
            var obj = (this.outputClass != null)
                    ? this.objectMapper.readValue(llmResponse, this.outputClass)
                    : this.objectMapper.readValue(llmResponse, this.outputType);

            return Optional.ofNullable(obj);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
