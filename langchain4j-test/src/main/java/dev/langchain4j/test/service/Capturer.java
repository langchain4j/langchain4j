package dev.langchain4j.test.service;

import static java.util.stream.Collectors.joining;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.test.service.AiServiceCapture.ThrowingRunnable;
import dev.langchain4j.test.service.internal.InvocationRecord;
import dev.langchain4j.test.service.internal.InvocationRegistry;
import dev.langchain4j.test.service.internal.RecordingSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the lambda of {@link AiServiceCapture#capture(Callable)} and captures the AI Service
 * invocations it starts.
 */
final class Capturer {

    private static final Logger LOG = LoggerFactory.getLogger(Capturer.class);
    private static final long PROGRESS_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();
    private static final long POLL_INTERVAL_MILLIS = 50;
    static final String STREAM_START_GRACE_PROPERTY = "langchain4j.test.capture.stream-start-grace-millis";
    static final String END_REPORT_GRACE_PROPERTY = "langchain4j.test.capture.end-report-grace-millis";
    static final String TIMEOUT_PROPERTY = "langchain4j.test.capture.timeout-millis";
    private static final Duration DEFAULT_STREAM_START_GRACE = Duration.ofSeconds(2);
    private static final Duration DEFAULT_END_REPORT_GRACE = Duration.ofSeconds(5);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    private Capturer() {}

    private static long streamStartGraceMillis() {
        return graceMillis(STREAM_START_GRACE_PROPERTY, DEFAULT_STREAM_START_GRACE);
    }

    private static long endReportGraceMillis() {
        return graceMillis(END_REPORT_GRACE_PROPERTY, DEFAULT_END_REPORT_GRACE);
    }

    private static long timeoutMillis() {
        return graceMillis(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT);
    }

    private static long deadline() {
        long timeoutMillis = timeoutMillis();
        return timeoutMillis == 0 ? Long.MAX_VALUE : nowMillis() + timeoutMillis;
    }

    private static long graceMillis(String property, Duration defaultValue) {
        String value = System.getProperty(property);
        if (value == null) {
            return defaultValue.toMillis();
        }
        try {
            long millis = Long.parseLong(value.trim());
            if (millis >= 0) {
                return millis;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new IllegalArgumentException(
                "The system property %s must be a number of milliseconds (0 or more), but is '%s'"
                        .formatted(property, value));
    }

    static Callable<Object> supplier(ThrowingRunnable code) {
        return () -> {
            code.run();
            return AiServiceCapture.LAMBDA_RETURNS_NOTHING;
        };
    }

    static AiServiceCapture capture(Callable<?> code, boolean expectingFailure) {
        long streamStartGraceMillis = streamStartGraceMillis();
        long endReportGraceMillis = endReportGraceMillis();
        long deadline = deadline();
        RecordingSession session = InvocationRegistry.newSession();
        try {
            Outcome outcome = run(code, session);
            if (outcome.thrown != null) {
                if (outcome.thrown instanceof Error || !isErrorOfTopLevelInvocation(outcome.thrown, session)) {
                    if (expectingFailure
                            && !(outcome.thrown instanceof Error)
                            && session.topLevelInvocations().isEmpty()) {
                        throw new AssertionError(
                                "Expected an AI Service invocation to fail. " + AiServiceCapture.nothingCaptured()
                                        + " The lambda threw " + outcome.thrown + " (see the cause).",
                                outcome.thrown);
                    }
                    throw sneakyThrow(outcome.thrown);
                }
            } else {
                failIfStreamNotStarted(session, streamStartGraceMillis);
            }
            awaitFinished(session, outcome, endReportGraceMillis, deadline);
            rethrowFailureOfReturnedValue(outcome, session, deadline);
            List<AiServiceInvocation> invocations = session.topLevelInvocations().stream()
                    .map(AiServiceInvocation::from)
                    .toList();
            List<Object> lambdaOutputItems = outcome.started instanceof DrainingSubscriber subscriber && subscriber.streamed
                    ? subscriber.items()
                    : null;
            AiServiceCapture capture =
                    new AiServiceCapture(outcome.returned, outcome.thrown, invocations, lambdaOutputItems);
            Throwable failure = invocations.stream()
                    .map(AiServiceInvocation::error)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (failure instanceof Error error) {
                throw error;
            }
            if (expectingFailure && failure == null) {
                throw new AssertionError("Expected an AI Service invocation to fail. "
                        + (invocations.isEmpty()
                                ? AiServiceCapture.nothingCaptured()
                                : "None of the captured invocations failed: " + invocations));
            }
            if (!expectingFailure && failure != null) {
                throw sneakyThrow(outcome.thrown != null ? outcome.thrown : failure);
            }
            return capture;
        } finally {
            InvocationRegistry.close(session);
        }
    }

    private static Outcome run(Callable<?> code, RecordingSession session) {
        RecordingSession previous = InvocationRegistry.startRecordingThread(session);
        try {
            Object returned = null;
            try {
                returned = code.call();
                return new Outcome(returned, start(returned), null);
            } catch (Throwable t) {
                return new Outcome(returned, null, t);
            }
        } finally {
            InvocationRegistry.stopRecordingThread(previous);
        }
    }

    private static Object start(Object returned) {
        if (returned instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture();
        }
        CompletableFuture<?> uni = Mutiny.subscribeToUni(returned);
        if (uni != null) {
            return uni;
        }
        Flow.Publisher<?> publisher =
                returned instanceof Flow.Publisher<?> flowPublisher ? flowPublisher : ReactiveStreams.toFlowPublisher(returned);
        if (publisher != null) {
            // a Mono holds a single value, like a future: its value is not streamed
            boolean streamed = !TextTypes.isAssignableTo(returned.getClass(), Set.of("reactor.core.publisher.Mono"));
            DrainingSubscriber subscriber = new DrainingSubscriber(streamed);
            publisher.subscribe(subscriber);
            return subscriber;
        }
        return null;
    }

    private static boolean isErrorOfTopLevelInvocation(Throwable thrown, RecordingSession recording) {
        Set<Throwable> errors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (InvocationRecord invocation : recording.topLevelInvocations()) {
            for (AiServiceEvent event : invocation.events()) {
                if (event instanceof AiServiceErrorEvent errorEvent) {
                    errors.add(errorEvent.error());
                }
            }
        }
        return Throwables.causalChain(thrown).stream().anyMatch(errors::contains);
    }

    private static void rethrowFailureOfReturnedValue(Outcome outcome, RecordingSession recording, long deadline) {
        Throwable failure = null;
        try {
            if (outcome.started instanceof CompletableFuture<?> future) {
                while (true) {
                    try {
                        future.get(waitMillis(deadline), TimeUnit.MILLISECONDS);
                        break;
                    } catch (TimeoutException e) {
                        failIfTimedOut(outcome, recording, deadline);
                        LOG.warn("Still waiting for the future returned by the lambda to complete");
                    }
                }
            } else if (outcome.started instanceof DrainingSubscriber subscriber) {
                while (!subscriber.awaitTermination(waitMillis(deadline))) {
                    failIfTimedOut(outcome, recording, deadline);
                    LOG.warn("Still waiting for the publisher returned by the lambda to complete");
                }
                failure = subscriber.error;
            }
        } catch (ExecutionException | CompletionException e) {
            failure = e.getCause() != null ? e.getCause() : e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(outcome.started);
            throw new IllegalStateException("Interrupted while waiting for the value returned by the lambda", e);
        } catch (RuntimeException e) {
            failure = e;
        }
        if (failure != null && !isErrorOfTopLevelInvocation(failure, recording) && !recording.anyCancelled()) {
            throw sneakyThrow(failure);
        }
    }

    private static void failIfStreamNotStarted(RecordingSession recording, long graceMillis) {
        Map<InvocationRecord, Boolean> tokenStreams = new IdentityHashMap<>();
        awaitStreamsStarted(recording, graceMillis, tokenStreams);
        for (InvocationRecord invocation : recording.unfinishedInvocations()) {
            if (isUnstartedTokenStream(invocation, tokenStreams)) {
                InvocationContext context = invocation.invocationContext();
                throw new IllegalStateException(
                        "The TokenStream returned by %s.%s was not started. Set the handlers your TokenStream needs and call start() in the lambda, for example: capture(() -> assistant.chat(\"Hi\").ignoreErrors().start()). If start() was called, the framework that implements the AI Service does not report the events of its TokenStream for this invocation."
                                .formatted(context.interfaceName(), context.methodName()));
            }
        }
    }

    private static void awaitStreamsStarted(
            RecordingSession recording, long graceMillis, Map<InvocationRecord, Boolean> tokenStreams) {
        // a TokenStream can be started on another thread, for example by an executor of the code under test
        long deadline = nowMillis() + graceMillis;
        try {
            while (recording.unfinishedInvocations().stream()
                            .anyMatch(invocation -> isUnstartedTokenStream(invocation, tokenStreams))
                    && nowMillis() - deadline < 0) {
                recording.awaitFinished(POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for AI Service invocations to start", e);
        }
    }

    private static boolean isUnstartedTokenStream(
            InvocationRecord invocation, Map<InvocationRecord, Boolean> tokenStreams) {
        return isUnstarted(invocation)
                && tokenStreams.computeIfAbsent(invocation, Capturer::isTokenStreamInvocation);
    }

    private static boolean isUnstarted(InvocationRecord invocation) {
        return !invocation.isFinished() && !invocation.hasIssuedRequest();
    }

    private static boolean isTokenStreamInvocation(InvocationRecord invocation) {
        return AiServiceMethods.returns(invocation.invocationContext(), TokenStream.class);
    }

    private static void awaitFinished(
            RecordingSession recording, Outcome outcome, long endReportGraceMillis, long deadline) {
        Map<InvocationRecord, Boolean> blocking = new IdentityHashMap<>();
        try {
            long nextProgressLog = nowMillis() + PROGRESS_INTERVAL_MILLIS;
            Long returnedValueTerminatedAt = null;
            Long lambdaReturnedAt = outcome.started == null ? nowMillis() : null;
            while (!recording.awaitFinished(POLL_INTERVAL_MILLIS)) {
                failIfTimedOut(outcome, recording, deadline);
                long now = nowMillis();
                if (lambdaReturnedAt != null && now - lambdaReturnedAt > endReportGraceMillis) {
                    for (InvocationRecord invocation : recording.unfinishedInvocations()) {
                        if (invocation.parent() == null
                                && blocking.computeIfAbsent(
                                        invocation, record -> TextTypes.blocks(record.invocationContext()))) {
                            throw notReportedEnd(invocation, null);
                        }
                    }
                }
                if (returnedValueTerminatedAt == null && hasTerminated(outcome.started)) {
                    returnedValueTerminatedAt = now;
                }
                if (returnedValueTerminatedAt != null && now - returnedValueTerminatedAt > endReportGraceMillis) {
                    List<InvocationRecord> unfinished = recording.unfinishedInvocations();
                    if (unfinished.isEmpty()) {
                        break;
                    }
                    throw notReportedEnd(unfinished.get(0), outcome.started);
                }
                if (now - nextProgressLog < 0) {
                    continue;
                }
                nextProgressLog = now + PROGRESS_INTERVAL_MILLIS;
                for (InvocationRecord invocation : recording.unfinishedInvocations()) {
                    InvocationContext context = invocation.invocationContext();
                    LOG.warn(
                            "Still waiting for the AI Service invocation {}.{} (id {}) to finish: it started {} seconds ago, and its last event was {}. It finishes with an AiServiceCompletedEvent, AiServiceErrorEvent or AiServiceCancelledEvent.",
                            context.interfaceName(),
                            context.methodName(),
                            context.invocationId(),
                            context.timestamp() == null
                                    ? "?"
                                    : Duration.between(context.timestamp(), Instant.now()).toSeconds(),
                            invocation.lastEvent().eventClass().getSimpleName());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(outcome.started);
            throw new IllegalStateException("Interrupted while waiting for AI Service invocations to finish", e);
        }
    }

    private static long waitMillis(long deadline) {
        return Math.max(1, Math.min(PROGRESS_INTERVAL_MILLIS, deadline - nowMillis()));
    }

    private static void failIfTimedOut(Outcome outcome, RecordingSession recording, long deadline) {
        if (nowMillis() - deadline < 0) {
            return;
        }
        cancel(outcome.started);
        String unfinished = recording.unfinishedInvocations().stream()
                .map(invocation -> {
                    InvocationContext context = invocation.invocationContext();
                    return "%s.%s (id %s, last event: %s)"
                            .formatted(
                                    context.interfaceName(),
                                    context.methodName(),
                                    context.invocationId(),
                                    invocation.lastEvent().eventClass().getSimpleName());
                })
                .collect(joining(", "));
        throw new IllegalStateException(
                "capture(...) timed out after %d ms. %s. Change the timeout with the JVM system property %s (in milliseconds, 0 for no timeout)."
                        .formatted(
                                timeoutMillis(),
                                unfinished.isEmpty()
                                        ? "The value returned by the lambda has not ended"
                                        : "These AI Service invocations have not finished: " + unfinished,
                                TIMEOUT_PROPERTY));
    }

    private static long nowMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static boolean hasTerminated(Object started) {
        if (started instanceof CompletableFuture<?> future) {
            return future.isDone();
        }
        return started instanceof DrainingSubscriber subscriber && subscriber.hasTerminated();
    }

    private static IllegalStateException notReportedEnd(InvocationRecord invocation, Object started) {
        InvocationContext context = invocation.invocationContext();
        Throwable cause = null;
        if (started instanceof CompletableFuture<?> future && future.isCompletedExceptionally()) {
            cause = future.handle((value, error) -> error).join();
        } else if (started instanceof DrainingSubscriber subscriber) {
            cause = subscriber.error;
        }
        return new IllegalStateException(
                "%s, but the AI Service invocation %s.%s (id %s) did not report its end with an AiServiceCompletedEvent, AiServiceErrorEvent or AiServiceCancelledEvent. The framework that implements the AI Service does not report it in this case."
                        .formatted(
                                started == null
                                        ? "The lambda has returned, and the AI Service method returns only when it is done"
                                        : "The value returned by the lambda has ended",
                                context.interfaceName(),
                                context.methodName(),
                                context.invocationId()),
                cause);
    }

    private static void cancel(Object started) {
        if (started instanceof CompletableFuture<?> future) {
            future.cancel(true);
        } else if (started instanceof DrainingSubscriber subscriber) {
            subscriber.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private record Outcome(Object returned, Object started, Throwable thrown) {}

    private static final class DrainingSubscriber implements Flow.Subscriber<Object> {

        private final boolean streamed;
        private final CountDownLatch terminated = new CountDownLatch(1);
        private final List<Object> items = new ArrayList<>();
        private volatile Flow.Subscription subscription;
        private volatile Throwable error;

        DrainingSubscriber(boolean streamed) {
            this.streamed = streamed;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        boolean hasTerminated() {
            return terminated.getCount() == 0;
        }

        void cancel() {
            Flow.Subscription subscription = this.subscription;
            if (subscription != null) {
                subscription.cancel();
            }
        }

        synchronized List<Object> items() {
            return new ArrayList<>(items);
        }

        @Override
        public void onNext(Object item) {
            if (!streamed) {
                return;
            }
            synchronized (this) {
                items.add(item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            terminated.countDown();
        }

        @Override
        public void onComplete() {
            terminated.countDown();
        }

        boolean awaitTermination(long timeoutMillis) throws InterruptedException {
            return terminated.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Subscribes to a Mutiny {@code Uni}, when Mutiny is on the classpath.
     */
    private static final class Mutiny {

        static CompletableFuture<?> subscribeToUni(Object object) {
            if (object == null) {
                return null;
            }
            Class<?> uniClass = ReactiveStreams.loadClass(object.getClass().getClassLoader(), "io.smallrye.mutiny.Uni");
            if (uniClass == null || !uniClass.isInstance(object)) {
                return null;
            }
            try {
                return (CompletableFuture<?>) uniClass.getMethod("subscribeAsCompletionStage").invoke(object);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("The Uni returned by the lambda could not be subscribed to", e);
            }
        }
    }

    /**
     * Converts a Reactive Streams publisher (for example a Reactor {@code Flux}) to a {@link Flow.Publisher}, when
     * Reactive Streams is on the classpath.
     */
    private static final class ReactiveStreams {

        static Flow.Publisher<?> toFlowPublisher(Object object) {
            if (object == null) {
                return null;
            }
            Class<?> publisherClass = loadClass(object.getClass().getClassLoader(), "org.reactivestreams.Publisher");
            if (publisherClass == null || !publisherClass.isInstance(object)) {
                return null;
            }
            Class<?> adapters = loadClass(object.getClass().getClassLoader(), "org.reactivestreams.FlowAdapters");
            if (adapters == null) {
                throw new IllegalStateException(("The lambda returned a Reactive Streams publisher (%s), but "
                                + "org.reactivestreams.FlowAdapters is not on the classpath, so capture(...) cannot "
                                + "subscribe to it: add org.reactivestreams:reactive-streams 1.0.3 or later")
                        .formatted(object.getClass().getName()));
            }
            try {
                return (Flow.Publisher<?>) adapters.getMethod("toFlowPublisher", publisherClass).invoke(null, object);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "The publisher returned by the lambda could not be subscribed to: " + object.getClass().getName(),
                        e);
            }
        }

        private static Class<?> loadClass(ClassLoader classLoader, String className) {
            try {
                return Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        }
    }
}
