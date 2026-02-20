package dev.langchain4j.observation.listeners;

import static dev.langchain4j.observation.listeners.ChatModelDocumentation.HighCardinalityValues.INPUT_TOKENS;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.HighCardinalityValues.OUTPUT_TOKENS;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.OPERATION_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.OUTCOME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.PROVIDER_NAME;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.REQUEST_MODEL;
import static dev.langchain4j.observation.listeners.ChatModelDocumentation.LowCardinalityValues.RESPONSE_MODEL;
import static java.util.Optional.ofNullable;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.jspecify.annotations.Nullable;

/**
 * This will decide how attributes will be extracted based on context data.
 */
public class DefaultChatModelConvention implements ChatModelConvention {

    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_ERROR = "ERROR";
    static final String UNKNOWN = "unknown";

    public DefaultChatModelConvention() {
    }

    @Override
    public @Nullable String getName() {
        return "gen_ai.client.operation.duration";
    }

    @Override
    public @Nullable String getContextualName(final ChatModelObservationContext context) {
        return "GenAI request duration";
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(final ChatModelObservationContext context) {
        final ChatModelRequestContext requestContext = context.getRequestContext();
        final ChatModelResponseContext responseContext = context.getResponseContext();
        final ChatModelErrorContext errorContext = context.getErrorContext();

        // fast fail
        if (requestContext == null) {
            return KeyValues.empty();
        }

        KeyValues result = KeyValues.of(KeyValue.of(OPERATION_NAME, "chat"));

        result = ofNullable(requestContext.modelProvider())
                .map(p -> KeyValue.of(PROVIDER_NAME, p.name()))
                .map(result::and).orElse(result.and(KeyValue.of(PROVIDER_NAME, UNKNOWN)));

        result = ofNullable(requestContext.chatRequest())
                .map(ChatRequest::parameters).map(ChatRequestParameters::modelName)
                .map(m -> KeyValue.of(REQUEST_MODEL, m))
                .map(result::and).orElse(result.and(KeyValue.of(REQUEST_MODEL, UNKNOWN)));

        result = ofNullable(responseContext)
                .map(ChatModelResponseContext::chatResponse).map(ChatResponse::metadata).map(ChatResponseMetadata::modelName)
                .map(m -> KeyValue.of(RESPONSE_MODEL, m))
                .map(result::and).orElse(result.and(KeyValue.of(RESPONSE_MODEL, UNKNOWN)));

        if (errorContext != null && errorContext.error() != null) {
            result = result.and(KeyValue.of(OUTCOME.asString(), OUTCOME_ERROR));
        } else {
            result = result.and(KeyValue.of(OUTCOME.asString(), OUTCOME_SUCCESS));
        }
        return result;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(final ChatModelObservationContext context) {
        final ChatModelResponseContext responseContext = context.getResponseContext();

        // fast fail
        if (responseContext == null) {
            return KeyValues.empty();
        }

        KeyValues result = KeyValues.empty();

        result = ofNullable(responseContext)
                .map(ChatModelResponseContext::chatResponse).map(ChatResponse::tokenUsage).map(TokenUsage::outputTokenCount)
                .map(tokens -> KeyValue.of(OUTPUT_TOKENS.asString(), "" + tokens))
                .map(result::and).orElse(result);

        result = ofNullable(responseContext)
                .map(ChatModelResponseContext::chatResponse).map(ChatResponse::tokenUsage).map(TokenUsage::inputTokenCount)
                .map(tokens -> KeyValue.of(INPUT_TOKENS.asString(), "" + tokens))
                .map(result::and).orElse(result);

        return result;
    }
}
