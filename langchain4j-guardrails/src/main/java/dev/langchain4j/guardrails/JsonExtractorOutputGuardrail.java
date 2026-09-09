package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.JsonParsingUtils.extractAndParseJson;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.internal.JsonParsingUtils;
import java.lang.reflect.Type;
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
 * <p>
 *     Deserialization uses a plain Jackson {@link ObjectMapper} with its default settings. A
 *     guardrail exists to reject output that does not fit the expected shape, so it deliberately
 *     does not use LangChain4j's own JSON codec, which is more forgiving: that codec reads private
 *     fields without setters, accepts enum constants case-insensitively, and parses dates an LLM
 *     has written in a field-wise form. Output this guardrail rejects should stay rejected.
 * </p>
 * <p>
 *     One consequence is that this guardrail always uses Jackson 2, and is the one part of
 *     LangChain4j that the {@code langchain4j-jackson3} opt-in does not switch over.
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
    private Type reflectedType;
    private TypeReference<T> outputType;

    /**
     * @deprecated use {@link #JsonExtractorOutputGuardrail(Class)}, which does not expose Jackson
     * types in its signature. It parses with a default {@link ObjectMapper}, so pass one here only
     * if you need a mapper configured differently.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public JsonExtractorOutputGuardrail(ObjectMapper objectMapper, Class<T> outputClass) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
        this.outputClass = ensureNotNull(outputClass, "outputClass");
    }

    /**
     * @deprecated use {@link #JsonExtractorOutputGuardrail(Type)}, which does not expose Jackson
     * types in its signature. Pass {@code new TypeReference<Foo>() {}.getType()} to migrate, and
     * pass a mapper here only if you need one configured differently from the default.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public JsonExtractorOutputGuardrail(ObjectMapper objectMapper, TypeReference<T> outputType) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
        this.outputType = ensureNotNull(outputType, "outputType");
    }

    /**
     * Deserializes with a plain Jackson {@link ObjectMapper}, as this guardrail has always done.
     */
    public JsonExtractorOutputGuardrail(Class<T> outputClass) {
        this.objectMapper = new ObjectMapper();
        this.outputClass = ensureNotNull(outputClass, "outputClass");
    }

    /**
     * Deserializes with a plain Jackson {@link ObjectMapper}, matching
     * {@link #JsonExtractorOutputGuardrail(Class)}.
     */
    public JsonExtractorOutputGuardrail(Type outputType) {
        this.objectMapper = new ObjectMapper();
        this.reflectedType = ensureNotNull(outputType, "outputType");
    }

    /**
     * @deprecated use {@link #JsonExtractorOutputGuardrail(Type)}, which does not expose Jackson
     * types - {@code TypeReference} lives in Jackson's core package, which moved in Jackson 3.
     * Pass {@code new TypeReference<Foo>() {}.getType()} to migrate.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public JsonExtractorOutputGuardrail(TypeReference<T> outputType) {
        this(new ObjectMapper(), outputType);
    }

    private T parse(String text) throws Exception {
        if (outputClass != null) {
            return objectMapper.readValue(text, outputClass);
        }
        if (outputType != null) {
            return objectMapper.readValue(text, outputType);
        }
        return objectMapper.readValue(text, objectMapper.constructType(reflectedType));
    }

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        var llmResponse = ensureNotNull(responseFromLLM, "responseFromLLM").text();
        LOGGER.debug("LLM output: {}", llmResponse);

        return deserialize(llmResponse)
                .map(r -> successWith(r.json(), r.value()))
                .orElseGet(() -> invokeInvalidJson(responseFromLLM, llmResponse));
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
    protected Optional<JsonParsingUtils.ParsedJson<T>> deserialize(String llmResponse) {
        try {
            return Optional.of(extractAndParseJson(llmResponse, this::parse));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
