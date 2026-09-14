package dev.langchain4j.service;

import static dev.langchain4j.service.ToolErrorHandlingNotice.Default.TOOL_ARGUMENTS_ERROR;
import static dev.langchain4j.service.ToolErrorHandlingNotice.Default.TOOL_EXECUTION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * When the notice is logged, and what it says. Whether it reaches the log is covered by
 * {@link ToolErrorHandlingNoticeLoggingTest}.
 */
class ToolErrorHandlingNoticeTest {

    interface Assistant {

        String chat(String userMessage);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String userMessage);
    }

    interface PartiallyAsyncAssistant {

        String chat(String userMessage);

        CompletableFuture<String> chatAsync(String userMessage);
    }

    interface AsyncAssistantWithMemoryAccess extends ChatMemoryAccess {

        CompletableFuture<String> chat(String userMessage);
    }

    /**
     * What {@code DefaultAiServices} passes in, without the third-party adapters that are not on the
     * test classpath.
     */
    private static final Predicate<Type> ASYNCHRONOUS = returnType ->
            TypeUtils.typeHasRawClass(returnType, CompletableFuture.class)
                    || TypeUtils.typeHasRawClass(returnType, CompletionStage.class)
                    || TypeUtils.typeHasRawClass(returnType, Flow.Publisher.class);

    static class Tools {

        @Tool("Returns the weather in the given city")
        String weather(@P(name = "city") String city) {
            return "sunny";
        }
    }

    @Test
    void should_name_the_service_and_recommend_the_behavior_the_defaults_are_planned_to_change_to() {

        String message = ToolErrorHandlingNotice.message(
                Assistant.class, List.of(TOOL_ARGUMENTS_ERROR, TOOL_EXECUTION_ERROR));

        assertThat(message).contains(Assistant.class.getName()).contains("are planned to change");
        assertThat(message)
                .as("recommends the behavior the defaults are planned to change to")
                .contains(".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())")
                .contains(".toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())");
        assertThat(message)
                .as("explains how a tool tells the LLM about a failure under the recommended setting")
                .contains("ToolErrorVisibleToLlm");
        assertThat(message)
                .as("also shows how to keep the current behavior")
                .contains(".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation())")
                .contains(".toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())");
        assertThat(message)
                .contains("https://docs.langchain4j.dev/tutorials/tools#error-handling")
                .contains(ToolErrorHandlingNotice.class.getName());
    }

    @Test
    void should_mention_only_the_defaults_that_were_not_chosen_explicitly() {

        String message = ToolErrorHandlingNotice.message(Assistant.class, List.of(TOOL_ARGUMENTS_ERROR));

        assertThat(message)
                .as("the tool execution default was chosen explicitly, so nothing is sent to the LLM")
                .doesNotContain("credentials embedded in error messages")
                .doesNotContain(".toolExecutionErrorHandler(");
        assertThat(message).contains(".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation())");
    }

    @Test
    void should_not_warn_when_both_error_handlers_are_configured() {

        AiServiceContext context = withTools(Assistant.class);
        context.toolService.argumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm());
        context.toolService.executionErrorHandler(ToolExecutionErrorHandler.failInvocation());

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context, ASYNCHRONOUS)).isEmpty();
    }

    @Test
    void should_warn_only_about_the_handler_that_is_missing() {

        AiServiceContext context = withTools(Assistant.class);
        context.toolService.executionErrorHandler(ToolExecutionErrorHandler.failInvocation());

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context, ASYNCHRONOUS)).containsExactly(TOOL_ARGUMENTS_ERROR);
    }

    @Test
    void should_not_warn_when_no_tools_are_configured() {

        AiServiceContext context = AiServiceContext.create(Assistant.class);

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context, ASYNCHRONOUS)).isEmpty();
    }

    @Test
    void should_warn_when_only_a_tool_provider_is_configured() {

        AiServiceContext context = AiServiceContext.create(Assistant.class);
        context.toolService.toolProvider(
                request -> ToolProviderResult.builder().build());

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context, ASYNCHRONOUS))
                .as("tools supplied by a ToolProvider are only known at invocation time, but the defaults still apply")
                .containsExactly(TOOL_ARGUMENTS_ERROR, TOOL_EXECUTION_ERROR);
    }

    @Test
    void should_not_warn_when_all_methods_are_asynchronous() {

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(withTools(AsyncAssistant.class), ASYNCHRONOUS))
                .as("asynchronous and reactive modes already behave the way the defaults are planned to behave")
                .isEmpty();
    }

    @Test
    void should_warn_when_at_least_one_method_is_blocking() {

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(withTools(PartiallyAsyncAssistant.class), ASYNCHRONOUS))
                .containsExactly(TOOL_ARGUMENTS_ERROR, TOOL_EXECUTION_ERROR);
    }

    private static AiServiceContext withTools(Class<?> aiServiceClass) {
        AiServiceContext context = AiServiceContext.create(aiServiceClass);
        context.toolService.tools(List.of(new Tools()));
        return context;
    }

    @Test
    void should_not_warn_for_an_asynchronous_service_that_also_exposes_chat_memory_access() {

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(
                        withTools(AsyncAssistantWithMemoryAccess.class), ASYNCHRONOUS))
                .as("the methods of ChatMemoryAccess are not AI Service methods")
                .isEmpty();
    }
}
