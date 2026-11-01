package dev.langchain4j.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static java.util.Map.entry;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.scoring.ScoringModel;
import java.net.http.HttpClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;

/**
 * Static gate for the non-blocking AI Service pipelines: the methods composing them must compose futures and
 * relay reactive events, never block. A single {@code Future.get()} or sync {@code chat()} sneaking into these
 * call graphs silently turns "non-blocking" into "thread-per-request" — these rules fail the build at the
 * offending line instead.
 * <p>
 * Two flows are covered:
 * <ul>
 *   <li>the {@code CompletableFuture} single-response flow in {@code DefaultAiServices} and {@code ToolService} —
 *       policed by <b>reachability</b> rather than by method name: every method whose signature returns or accepts a
 *       {@code CompletableFuture} / {@code CompletionStage} / {@code Flow.Publisher} is treated as an async entry
 *       point, and the whole transitive call graph reachable from those entry points (staying within the two
 *       classes) must not block. This automatically covers private helpers — and any future ones — without having
 *       to enumerate them, while the blocking sync / {@code TokenStream} methods (never reached from an async entry
 *       point) are correctly left alone;</li>
 *   <li>the reactive {@code Flow.Publisher} streaming flow ({@link AiServiceStreamingEventPublisher} and its
 *       nested/anonymous classes), which is async end-to-end, so every method in it is policed.</li>
 * </ul>
 * <p>
 * Complements {@link AiServicesNonBlockingTest} (runtime detection via BlockHound): these rules cannot see through
 * polymorphic calls into user SPIs, but they catch violations on code paths a test might not execute.
 */
class AsyncAiServicesArchTest {

    /** Return/parameter types that mark a method as part of the async pipeline. */
    private static final Set<String> ASYNC_SIGNATURE_TYPES = Set.of(
            CompletableFuture.class.getName(), CompletionStage.class.getName(), Flow.Publisher.class.getName());

    /** Blocking calls, keyed by the (statically-typed) call-site owner. */
    private static final Map<String, Set<String>> BLOCKING_CALLS = Map.ofEntries(
            entry(Future.class.getName(), Set.of("get", "join")),
            entry(CompletableFuture.class.getName(), Set.of("get", "join")),
            entry(Thread.class.getName(), Set.of("sleep", "join")),
            entry(Object.class.getName(), Set.of("wait")),
            entry(CountDownLatch.class.getName(), Set.of("await")),
            entry(Semaphore.class.getName(), Set.of("acquire", "acquireUninterruptibly")),
            entry(CyclicBarrier.class.getName(), Set.of("await")),
            entry(Lock.class.getName(), Set.of("lock", "lockInterruptibly")),
            entry(Condition.class.getName(), Set.of("await", "awaitUninterruptibly", "awaitNanos")),
            entry(ExecutorService.class.getName(), Set.of("awaitTermination", "invokeAll", "invokeAny")),
            entry(BlockingQueue.class.getName(), Set.of("put", "take")),
            entry(HttpClient.class.getName(), Set.of("send")),
            entry(ChatModel.class.getName(), Set.of("chat", "doChat")),
            entry(EmbeddingModel.class.getName(), Set.of("embed", "embedAll")),
            entry(ScoringModel.class.getName(), Set.of("score", "scoreAll")),
            entry(ModerationModel.class.getName(), Set.of("moderate")));

    /**
     * Blocking calls that are safe despite living (textually) inside a policed method, because they run inside a
     * lambda that is offloaded to an executor or deferred off the reactive path. ArchUnit folds a lambda body into
     * its declaring method, so it cannot tell these apart from an inline call; each entry is verified by hand and
     * keyed by {@code <declaringMethodName>|<blockingOwnerFqn>|<blockingMethodName>}.
     */
    private static final Set<String> OFFLOADED_OR_DEFERRED_BLOCKING = Set.of(
            // moderate() runs inside CompletableFuture.supplyAsync(..., executor) — offloaded to an executor thread.
            "triggerModerationIfNeeded|" + ModerationModel.class.getName() + "|moderate",
            // join() runs inside the cross-agent compensation callback handed to onCompensableToolExecution, which
            // the agentic layer invokes off the reactive path (see the comment at the call site).
            "processToolResults|" + CompletableFuture.class.getName() + "|join");

    @Test
    void async_pipeline_must_not_call_blocking_apis() {

        JavaClasses classes = importProductionServiceClasses();

        Set<String> asyncReachable = asyncReachableMethods(classes);

        ArchRule rule = methods()
                .that(isOneOf(
                        asyncReachable,
                        "reachable from the CompletableFuture/CompletionStage/Flow.Publisher entry points of "
                                + "DefaultAiServices and ToolService"))
                .should(notCallBlockingApis());

        rule.check(classes);
    }

    @Test
    void reactive_streaming_pipeline_must_not_call_blocking_apis() {

        JavaClasses classes = importProductionServiceClasses();

        ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat(reactiveStreamingPipelineClasses())
                .should(notCallBlockingApis());

        rule.check(classes);
    }

    private static JavaClasses importProductionServiceClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.langchain4j.service");
    }

    /**
     * Computes the transitive closure of methods reachable (via method calls that stay within
     * {@code DefaultAiServices} / {@code ToolService}) from the async entry points — the methods whose signature
     * returns or accepts a {@code CompletableFuture} / {@code CompletionStage} / {@code Flow.Publisher}. ArchUnit
     * attributes calls made inside a lambda to the enclosing method, so the {@code thenCompose(...)} chains are
     * followed transparently.
     */
    private static Set<String> asyncReachableMethods(JavaClasses classes) {
        Set<JavaCodeUnit> reached = new LinkedHashSet<>();
        Deque<JavaCodeUnit> queue = new ArrayDeque<>();
        for (JavaClass javaClass : classes) {
            if (!isAsyncPipelineClass(javaClass)) {
                continue;
            }
            for (JavaCodeUnit unit : javaClass.getCodeUnits()) {
                if (unit instanceof JavaMethod method && hasAsyncSignature(method) && reached.add(method)) {
                    queue.add(method);
                }
            }
        }
        while (!queue.isEmpty()) {
            JavaCodeUnit unit = queue.poll();
            if (!(unit instanceof JavaMethod method)) {
                continue;
            }
            for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                if (!isAsyncPipelineClass(call.getTargetOwner())) {
                    continue;
                }
                call.getTarget().resolveMember().ifPresent(callee -> {
                    if (reached.add(callee)) {
                        queue.add(callee);
                    }
                });
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (JavaCodeUnit unit : reached) {
            names.add(unit.getFullName());
        }
        return names;
    }

    private static boolean hasAsyncSignature(JavaMethod method) {
        if (ASYNC_SIGNATURE_TYPES.contains(method.getRawReturnType().getFullName())) {
            return true;
        }
        return method.getRawParameterTypes().stream()
                .anyMatch(parameter -> ASYNC_SIGNATURE_TYPES.contains(parameter.getFullName()));
    }

    private static boolean isAsyncPipelineClass(JavaClass javaClass) {
        String name = javaClass.getFullName();
        return name.startsWith("dev.langchain4j.service.DefaultAiServices")
                || name.startsWith("dev.langchain4j.service.tool.ToolService");
    }

    private static DescribedPredicate<JavaMethod> isOneOf(Set<String> methodFullNames, String description) {
        return new DescribedPredicate<>(description) {
            @Override
            public boolean test(JavaMethod method) {
                return methodFullNames.contains(method.getFullName());
            }
        };
    }

    private static DescribedPredicate<JavaClass> reactiveStreamingPipelineClasses() {
        return new DescribedPredicate<>("the reactive streaming AI Service publisher "
                + "(AiServiceStreamingEventPublisher, including its nested/anonymous classes)") {

            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getFullName().startsWith("dev.langchain4j.service.AiServiceStreamingEventPublisher");
            }
        };
    }

    private static ArchCondition<JavaMethod> notCallBlockingApis() {
        return new ArchCondition<>("not call blocking APIs (the async pipeline must compose futures, not block)") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (isBlocking(call) && !isOffloadedOrDeferred(method, call)) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                String.format(
                                        "%s calls blocking %s.%s() in %s",
                                        method.getFullName(),
                                        call.getTargetOwner().getName(),
                                        call.getName(),
                                        call.getSourceCodeLocation())));
                    }
                }
            }
        };
    }

    private static boolean isBlocking(JavaMethodCall call) {
        Set<String> blockingNames = BLOCKING_CALLS.get(call.getTargetOwner().getName());
        return blockingNames != null && blockingNames.contains(call.getName());
    }

    private static boolean isOffloadedOrDeferred(JavaMethod method, JavaMethodCall call) {
        String key = method.getName() + "|" + call.getTargetOwner().getName() + "|" + call.getName();
        return OFFLOADED_OR_DEFERRED_BLOCKING.contains(key);
    }
}
