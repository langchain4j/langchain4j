package dev.langchain4j.micrometer.observation;

import java.util.Map;
import dev.langchain4j.micrometer.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

/**
 * An {@link ObservationHandler} that records token usage metrics for chat model interactions.
 * <p>
 * This handler records the following metrics:
 * <ul>
 *   <li>{@code gen_ai.client.token.usage} - The number of tokens used, tagged by token type (input/output)</li>
 * </ul>
 * <p>
 * Request/error counting and duration metrics are handled by the default Micrometer
 * observation handlers via the {@code outcome} tag on the observation.
 * <p>
 * This handler should be registered with the {@link io.micrometer.observation.ObservationRegistry}
 * once (e.g., via Spring Boot auto-configuration).
 */
public class ChatModelMeterObservationHandler implements ObservationHandler<ChatModelObservationContext> {

    private final MeterRegistry meterRegistry;

    public ChatModelMeterObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(ChatModelObservationContext context) {
        // No action needed - observation API handles timing automatically
    }

    @Override
    public void onError(ChatModelObservationContext context) {
        // No action needed - observation API handles error counting via outcome tag
    }

    @Override
    public void onStop(ChatModelObservationContext context) {
        addTokenUsageMetrics(context);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    private void addTokenUsageMetrics(ChatModelObservationContext context) {
        ChatModelResponseContext responseContext = context.getResponseContext();
        if (responseContext != null && responseContext.chatResponse().tokenUsage() != null) {
            addTokenMetric(
                    responseContext,
                    OTelGenAiTokenType.INPUT,
                    responseContext.chatResponse().tokenUsage().inputTokenCount(),
                    "Measures the number of input tokens used");
            addTokenMetric(
                    responseContext,
                    OTelGenAiTokenType.OUTPUT,
                    responseContext.chatResponse().tokenUsage().outputTokenCount(),
                    "Measures the number of output tokens used");
        }
    }

    private void addTokenMetric(
            ChatModelResponseContext responseContext,
            OTelGenAiTokenType tokenType,
            int tokenCount,
            String description) {
        Counter.builder(OTelGenAiMetricName.TOKEN_USAGE.value())
                .tag(OTelGenAiAttributes.OPERATION_NAME.value(), OTelGenAiOperationName.CHAT.value())
                .tag(OTelGenAiAttributes.SYSTEM.value(), getSystemValue(responseContext.attributes()))
                .tag(
                        OTelGenAiAttributes.REQUEST_MODEL.value(),
                        responseContext.chatRequest().parameters().modelName())
                .tag(
                        OTelGenAiAttributes.RESPONSE_MODEL.value(),
                        responseContext.chatResponse().metadata().modelName())
                .tag(OTelGenAiAttributes.TOKEN_TYPE.value(), tokenType.value())
                .description(description)
                .register(meterRegistry)
                .increment(tokenCount);
    }

    private String getSystemValue(Map<Object, Object> attributes) {
        return (String) attributes.get(OTelGenAiAttributes.SYSTEM);
    }
}
