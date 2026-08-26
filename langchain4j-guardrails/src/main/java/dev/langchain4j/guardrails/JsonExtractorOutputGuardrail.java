package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.JsonParsingUtils.extractAndParseJson;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.internal.Json;
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
 *     Deserialization goes through LangChain4j's configured JSON codec - the same one AI Services
 *     use to read structured output - so that swapping the JSON library applies here too. That
 *     codec is deliberately more forgiving than a bare Jackson {@code ObjectMapper}: it reads
 *     private fields without setters, accepts enum constants case-insensitively, and parses dates
 *     that an LLM has written in a field-wise form. It does still reject unknown properties, which
 *     is the check that catches a hallucinated field.
 * </p>
 * <p>
 *     If you need the stricter behaviour of a plain {@code ObjectMapper}, configure one and pass it
 *     to {@link #JsonExtractorOutputGuardrail(ObjectMapper, Class)}, which is deprecated but is
 *     honoured for as long as it exists.
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
     * types. Deserialization then goes through LangChain4j's configured JSON codec, which can be
     * swapped for Jackson 3. Note that the codec is configured differently from a plain
     * {@code ObjectMapper}: see the class javadoc.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public JsonExtractorOutputGuardrail(ObjectMapper objectMapper, Class<T> outputClass) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
        this.outputClass = ensureNotNull(outputClass, "outputClass");
    }

    /**
     * @deprecated use {@link #JsonExtractorOutputGuardrail(Type)}, which does not expose Jackson
     * types. Pass {@code new TypeReference<Foo>() {}.getType()} to migrate. Deserialization then
     * goes through LangChain4j's configured JSON codec, which can be swapped for Jackson 3. Note
     * that the codec is configured differently from a plain {@code ObjectMapper}: see the class
     * javadoc.
     */
    @Deprecated(since = "1.20.0", forRemoval = true)
    public JsonExtractorOutputGuardrail(ObjectMapper objectMapper, TypeReference<T> outputType) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
        this.outputType = ensureNotNull(outputType, "outputType");
    }

    /**
     * Deserializes with LangChain4j's configured JSON codec, so that swapping the JSON library -
     * for example by putting {@code langchain4j-json-jackson3} on the classpath - applies here too.
     */
    public JsonExtractorOutputGuardrail(Class<T> outputClass) {
        this.objectMapper = null;
        this.outputClass = ensureNotNull(outputClass, "outputClass");
    }

    /**
     * Deserializes with LangChain4j's configured JSON codec, so that swapping the JSON library
     * applies here too.
     */
    public JsonExtractorOutputGuardrail(Type outputType) {
        this.objectMapper = null;
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

    @SuppressWarnings("unchecked")
    private T parse(String text) throws Exception {
        if (objectMapper != null) {
            return outputClass != null
                    ? objectMapper.readValue(text, outputClass)
                    : objectMapper.readValue(text, outputType);
        }
        return outputClass != null
                ? Json.fromJson(text, outputClass)
                : (T) Json.fromJson(text, reflectedType);
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
