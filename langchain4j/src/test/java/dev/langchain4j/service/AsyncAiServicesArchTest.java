package dev.langchain4j.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Static gate for the asynchronous AI Service pipeline: the methods composing the async flow
 * ({@code invokeAsync}, {@code executeInferenceAndToolsLoopAsync}, {@code executeToolsAsync} and the
 * lambdas they contain) must compose futures, never block on them. A single {@code Future.get()} or
 * sync {@code chat()} sneaking into this call graph silently turns "non-blocking" into
 * "thread-per-request" — this rule fails the build at the offending line instead.
 * <p>
 * Complements {@link AiServicesNonBlockingTest} (runtime detection via BlockHound): this rule cannot see
 * through polymorphic calls into user SPIs, but it catches violations on code paths a test might not execute.
 */
class AsyncAiServicesArchTest {

    private static final Set<String> FUTURE_TYPES =
            Set.of(CompletableFuture.class.getName(), Future.class.getName());

    @Test
    void async_pipeline_must_not_call_blocking_apis() {

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("dev.langchain4j.service");

        ArchRule rule = methods()
                .that()
                .areDeclaredInClassesThat(asyncAiServicePipelineClasses())
                .and()
                .haveNameMatching(".*Async.*")
                .should(notCallBlockingApis());

        rule.check(classes);
    }

    private static DescribedPredicate<JavaClass> asyncAiServicePipelineClasses() {
        return new DescribedPredicate<>("the AI Service async pipeline classes "
                + "(DefaultAiServices and ToolService, including their nested/anonymous classes)") {

            @Override
            public boolean test(JavaClass javaClass) {
                String name = javaClass.getFullName();
                return name.startsWith("dev.langchain4j.service.DefaultAiServices")
                        || name.startsWith("dev.langchain4j.service.tool.ToolService");
            }
        };
    }

    private static ArchCondition<JavaMethod> notCallBlockingApis() {
        return new ArchCondition<>("not call blocking APIs (the async pipeline must compose futures, not block)") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (isBlocking(call)) {
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
        String owner = call.getTargetOwner().getName();
        String name = call.getName();
        if (FUTURE_TYPES.contains(owner) && (name.equals("get") || name.equals("join"))) {
            return true;
        }
        if (owner.equals(Thread.class.getName()) && name.equals("sleep")) {
            return true;
        }
        if (owner.equals(Object.class.getName()) && name.equals("wait")) {
            return true;
        }
        if (owner.equals(CountDownLatch.class.getName()) && name.equals("await")) {
            return true;
        }
        if (owner.equals(ChatModel.class.getName()) && (name.equals("chat") || name.equals("doChat"))) {
            return true;
        }
        return false;
    }
}
