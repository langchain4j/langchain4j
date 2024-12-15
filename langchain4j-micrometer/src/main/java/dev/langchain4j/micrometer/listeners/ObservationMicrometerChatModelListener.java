package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.micrometer.conventions.AiObservationMetricAttributes;
import dev.langchain4j.micrometer.conventions.AiObservationMetricNames;
import dev.langchain4j.micrometer.conventions.AiProvider;
import dev.langchain4j.micrometer.conventions.AiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class ObservationMicrometerChatModelListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;
    private static final String DESCRIPTION = "Measures number of input and output tokens used";
    private final ObservationRegistry observationRegistry;
    private final AtomicReference<Observation.Scope> scope;

    public ObservationMicrometerChatModelListener(final MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.scope = new AtomicReference<>();
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        Observation observation = Observation.createNotStarted("gen_ai.client.operation.duration", observationRegistry)
                .lowCardinalityKeyValue("gen_ai.operation.name", "chat")
                .lowCardinalityKeyValue("gen_ai.system", "langchain4j")
                .lowCardinalityKeyValue("gen_ai.request.model", requestContext.request().model())
                .contextualName("GenAI operation duration")
                .start();

        scope.set(observation.openScope());

        addAiProvider(requestContext, observation);

        addRequestMetrics(requestContext);
    }

    private void addAiProvider(ChatModelRequestContext requestContext, Observation observation) {
        final ChatModelRequest request = requestContext.request();
        System.out.println("Request classname: " + request.getClass().getSimpleName()); // Request classname: ChatModelRequest

        // ChatRequest (will be used instead of ChatModelRequest in ChatModelRequestContext)
        // OpenAiChatRequest extends ChatRequest
        // OpenAiChatResponse extends ChatResponse

        final String className = requestContext.request().getClass().getSimpleName();// ChatRequest
        String providerClassNamePrefix = className.endsWith("ChatRequest")
                ? className.substring(0, className.length() - "ChatRequest".length()).toLowerCase()
                : className.toLowerCase();

        Arrays.stream(AiProvider.values())
                .filter(provider -> provider.name().toLowerCase().equals(providerClassNamePrefix))
                .findFirst()
                .ifPresent(provider -> {
                    observation.lowCardinalityKeyValue("gen_ai.provider", provider.value());
                    requestContext.attributes().put("gen_ai.provider", provider.value());
                });
    }

    private void addAiProvider(ChatModelResponseContext responseContext, Observation observation) {
        final ChatModelRequest response = responseContext.request();
        System.out.println("Response classname: " + response.getClass().getSimpleName()); // Request classname: ChatModelRequest

        // ChatRequest (will be used instead of ChatModelRequest in ChatModelRequestContext)
        // OpenAiChatRequest extends ChatRequest
        // OpenAiChatResponse extends ChatResponse

        final String className = responseContext.request().getClass().getSimpleName();// ChatRequest
        String providerClassNamePrefix = className.endsWith("ChatResponse")
                ? className.substring(0, className.length() - "ChatResponse".length()).toLowerCase()
                : className.toLowerCase();

        Arrays.stream(AiProvider.values())
                .filter(provider -> provider.name().toLowerCase().equals(providerClassNamePrefix))
                .findFirst()
                .ifPresent(provider -> {
                    observation.lowCardinalityKeyValue("gen_ai.provider", provider.value());
                    responseContext.attributes().put("gen_ai.provider", provider.value());
                });
    }

    private void addRequestMetrics(ChatModelRequestContext requestContext) {
        if (requestContext.request() != null) {
            Counter.builder("langchain4j.chat.model.request")
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", requestContext.attributes().get("gen_ai.provider") != null
                            ? String.valueOf(requestContext.attributes().get("gen_ai.provider"))
                            : "langchain4j")
                    .tag("gen_ai.request.model", requestContext.request().model())
                    .description("The number of requests that were made to the chat model")
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        Observation.Scope scope = this.scope.getAndSet(null);
        if (scope != null) {
            Observation observation = scope.getCurrentObservation();
            if (observation != null) {
                observation.lowCardinalityKeyValue("gen_ai.response.model", responseContext.response().model())
                        .stop();
                addAiProvider(responseContext, scope.getCurrentObservation());
            }
        }

        addResponseMetrics(responseContext);
    }

    private void addResponseMetrics(ChatModelResponseContext responseContext) {
        if (responseContext.response().tokenUsage() != null) {
            Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", responseContext.attributes().get("gen_ai.provider") != null
                            ? String.valueOf(responseContext.attributes().get("gen_ai.provider"))
                            : "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().inputTokenCount());

            Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().outputTokenCount());

            Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().totalTokenCount());
        }
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        Observation.Scope scope = this.scope.getAndSet(null);
        if (scope != null) {
            Observation observation = scope.getCurrentObservation();
            if (observation != null) {
                observation.lowCardinalityKeyValue("gen_ai.request.model", errorContext.request().model())
                        .lowCardinalityKeyValue("error.type", errorContext.error().getClass().getSimpleName());
                observation.error(errorContext.error());

                observation.stop();
            }
        }

        addErrorMetrics(errorContext);
    }

    private void addErrorMetrics(ChatModelErrorContext errorContext) {
        if (errorContext.request() != null) {
            Counter.builder("langchain4j.chat.model.error")
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", errorContext.request().model())
                    .description("The number of errors that occurred in the chat model")
                    .register(meterRegistry)
                    .increment();
        }
    }
}
