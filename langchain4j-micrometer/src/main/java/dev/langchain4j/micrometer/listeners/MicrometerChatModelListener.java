package dev.langchain4j.micrometer.listeners;

import dev.langchain4j.micrometer.conventions.OTelGenAiMetricAttributes;
import dev.langchain4j.micrometer.conventions.OTelGenAiMetricNames;
import dev.langchain4j.micrometer.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class MicrometerChatModelListener implements ChatModelListener {

    private final MeterRegistry meterRegistry;
    private static final String DESCRIPTION = "Measures number of input and output tokens used";
    private final ThreadLocal<Instant> requestStartTime;

    public MicrometerChatModelListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.requestStartTime = new ThreadLocal<>();
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        ChatModelListener.super.onRequest(requestContext);
        requestStartTime.set(Instant.now());

        Counter.builder("langchain4j.chat.model.request")
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.system", "langchain4j")
                .tag("gen_ai.request.model", requestContext.request().model())
                .description("The number of requests that were made to the chat model")
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        Instant start = requestStartTime.get();
        if (start != null) {
            Timer.builder("gen_ai.client.operation.duration")
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .description("GenAI operation duration")
                    .register(meterRegistry)
                    .record(Duration.between(start, Instant.now()).toSeconds(), TimeUnit.SECONDS);
            requestStartTime.remove();
        }

        if (responseContext.response().tokenUsage() != null) {
            Counter.builder(OTelGenAiMetricNames.TOKEN_USAGE.value())
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .tag(OTelGenAiMetricAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.INPUT.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().inputTokenCount());

            Counter.builder(OTelGenAiMetricNames.TOKEN_USAGE.value())
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .tag(OTelGenAiMetricAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.OUTPUT.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().outputTokenCount());

            Counter.builder(OTelGenAiMetricNames.TOKEN_USAGE.value())
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", responseContext.request().model())
                    .tag("gen_ai.response.model", responseContext.response().model())
                    .tag(OTelGenAiMetricAttributes.TOKEN_TYPE.value(), OTelGenAiTokenType.TOTAL.value())
                    .description(DESCRIPTION)
                    .register(meterRegistry)
                    .increment(responseContext.response().tokenUsage().totalTokenCount());
        }
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        Instant start = requestStartTime.get();
        if (start != null) {
            Timer.builder("gen_ai.client.operation.duration")
                    .tag("gen_ai.operation.name", "chat")
                    .tag("gen_ai.system", "langchain4j")
                    .tag("gen_ai.request.model", errorContext.request().model())
                    .tag("error.type", errorContext.error().getClass().getSimpleName())
                    .description("GenAI operation duration")
                    .register(meterRegistry)
                    .record(Duration.between(start, Instant.now()).toSeconds(), TimeUnit.SECONDS);
            requestStartTime.remove();
        }

        Counter.builder("langchain4j.chat.model.error")
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.system", "langchain4j")
                .tag("gen_ai.request.model", errorContext.request().model())
                .description("The number of errors that occurred during the chat")
                .register(meterRegistry)
                .increment();
    }
}
