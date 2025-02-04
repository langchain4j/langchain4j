package dev.langchain4j.langfuse;

import dev.langchain4j.langfuse.model.*;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLangfuseTracer implements LangfuseTracer {
    private static final Logger log = LoggerFactory.getLogger(DefaultLangfuseTracer.class);

    private final LangfuseClient client;
    private final LangfuseConfig config;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<String, TracingContext> contexts;
    private final BlockingQueue<Observation> observationQueue;
    private final AtomicBoolean isShutdown;

    public DefaultLangfuseTracer(LangfuseConfig config) {
        this.config = config;
        this.client = LangfuseClient.builder()
                .publicKey(config.getPublicKey())
                .secretKey(config.getSecretKey())
                .endpoint(config.getEndpoint())
                .build();

        this.contexts = new ConcurrentHashMap<>();
        this.observationQueue = new LinkedBlockingQueue<>(1000);
        this.isShutdown = new AtomicBoolean(false);
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(
                this::flush, config.getFlushInterval(), config.getFlushInterval(), TimeUnit.MILLISECONDS);
    }

    @Override
    public String startTrace(String name, Map<String, Object> inputs, String status) {
        String traceId = UUID.randomUUID().toString();
        TracingContext context = new TracingContext(TracingContext.builder().traceId(traceId));
        contexts.put(traceId, context);

        Trace createTrace =
                Trace.builder().id(traceId).name(name).inputs(inputs).build();

        try {
            client.trace().create(createTrace);
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("Started trace {} with name {}", traceId, name);

        return traceId;
    }

    @Override
    public void endTrace(String traceId, Map<String, Object> output, String status) {
        try {
            client.trace().update(traceId, output, "SUCCESS");
            contexts.remove(traceId);
            log.debug("Ended trace {}", traceId);
        } catch (Exception e) {
            log.error("Failed to end trace {}", traceId, e);
        }
    }

    @Override
    public String logEvent(String traceId, String name, Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        TracingContext context = getContext(traceId);

        Observation event = Observation.builder()
                .id(eventId)
                .traceId(traceId)
                .type(ObservationType.EVENT)
                .name(name)
                .metadata(data)
                .build();

        queueObservation(event);
        return eventId;
    }

    @Override
    public String startSpan(String traceId, String name, Map<String, Object> input, String status) {
        String spanId = UUID.randomUUID().toString();
        TracingContext context = getContext(traceId);

        Observation span = Observation.builder()
                .id(spanId)
                .traceId(traceId)
                .type(ObservationType.SPAN)
                .name(name)
                .input(input)
                .startTime(Instant.ofEpochMilli(System.currentTimeMillis()))
                .status(status)
                .build();

        context.pushObservation(span);
        queueObservation(span);
        return spanId;
    }

    @Override
    public String startSpan(
            final String traceId,
            final String name,
            final Map<String, Object> input,
            final String parentSpanId,
            final String status) {
        return null;
    }

    @Override
    public void endSpan(String spanId, Map<String, Object> output, String status) {
        try {
            client.observation().update(spanId, output, status);
            log.debug("Ended span {}", spanId);
        } catch (Exception e) {
            log.error("Failed to end span {}", spanId, e);
        }
    }

    @Override
    public String logGeneration(String traceId, Generation params) {
        String genId = UUID.randomUUID().toString();
        TracingContext context = getContext(traceId);

        Observation generation = Observation.builder()
                .id(genId)
                .traceId(traceId)
                .type(ObservationType.GENERATION)
                .name("llm_generation")
                .metadata(Map.of(
                        "Model",
                        params.getModel(),
                        "prompt",
                        params.getPrompt(),
                        "completion",
                        params.getCompletion(),
                        "promptTokens",
                        params.getPromptTokens(),
                        "completionTokens",
                        params.getCompletionTokens()))
                .startTime(Instant.ofEpochMilli(System.currentTimeMillis()))
                .build();

        queueObservation(generation);
        return genId;
    }

    @Override
    public String createSession(String name, Map<String, Object> metadata) {
        String sessionId = UUID.randomUUID().toString();
        Session session = Session.builder()
                .setId(sessionId)
                .setName(name)
                .addMetadata(metadata)
                .build();

        try {
            client.session().create(session);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sessionId;
    }

    @Override
    public void addTraceToSession(String sessionId, String traceId) {
        try {
            client.session().addTrace(sessionId, traceId);
            TracingContext context = contexts.get(traceId);
            if (context != null) {
                context.setSessionId(sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to add trace {} to session {}", traceId, sessionId, e);
        }
    }

    @Override
    public void scoreTrace(String traceId, String name, Object value) {
        Score score = Score.builder().traceId(traceId).name(name).value(value).build();

        try {
            client.score().create(score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void scoreObservation(String traceId, String observationId, String name, Object value) {
        Score score = Score.builder()
                .traceId(traceId)
                .observationId(observationId)
                .name(name)
                .value(value)
                .build();

        try {
            client.score().create(score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setTag(final String traceId, final String name, final String value) {
        return;
    }

    @Override
    public void updateSpan(final String spanId, final Map<String, Object> updates, final String status) {
        return;
    }

    @Override
    public synchronized void flush() {
        if (isShutdown.get()) {
            return;
        }

        List<Observation> batch = new ArrayList<>();
        observationQueue.drainTo(batch, config.getBatchSize());

        if (!batch.isEmpty()) {
            try {
                client.observation().createBatch(batch);
                log.debug("Flushed {} observations", batch.size());
            } catch (Exception e) {
                log.error("Failed to flush observations", e);
            }
        }
    }

    @Override
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            try {
                scheduler.shutdown();
                flush();
                client.shutdown();
                log.info("Langfuse tracer shutdown completed");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }
    }

    private TracingContext getContext(String traceId) {
        return contexts.computeIfAbsent(
                traceId, id -> new TracingContext(TracingContext.builder().traceId(id)));
    }

    private void queueObservation(Observation observation) {
        try {
            if (!observationQueue.offer(observation)) {
                log.warn("Observation queue is full, forcing flush");
                flush();
                if (!observationQueue.offer(observation)) {
                    log.error("Failed to queue observation after flush");
                }
            }
        } catch (Exception e) {
            log.error("Failed to queue observation", e);
        }
    }
}
