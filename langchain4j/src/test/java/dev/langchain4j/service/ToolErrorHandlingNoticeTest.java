package dev.langchain4j.service;

import static dev.langchain4j.service.ToolErrorHandlingNotice.Default.TOOL_ARGUMENTS_ERROR;
import static dev.langchain4j.service.ToolErrorHandlingNotice.Default.TOOL_EXECUTION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Verifies the one-time notice logged when an AI Service is built with tools but without explicitly
 * configured tool error handlers.
 * <p>
 * The tests that assert on the log output capture {@link System#err} (the default tinylog destination
 * for WARN), so the whole class runs {@link Isolated} and single-threaded to avoid races with the
 * otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class ToolErrorHandlingNoticeTest {

    interface Assistant {

        String chat(String userMessage);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String userMessage);
    }

    static class Tools {

        @Tool("Returns the weather in the given city")
        String weather(@P(name = "city") String city) {
            return "sunny";
        }
    }

    private static final ChatModel CHAT_MODEL = new ChatModel() {};

    @BeforeEach
    void resetNotice() {
        ToolErrorHandlingNotice.ALREADY_LOGGED.set(false);
    }

    @Test
    void should_warn_when_tools_are_configured_without_error_handlers() {

        String logOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .build());

        assertThat(logOutput)
                .as("names the AI Service and states that the defaults are going to change")
                .contains("WARN")
                .contains(Assistant.class.getName())
                .contains("are planned to change");
        assertThat(logOutput)
                .as("recommends the behavior the defaults are planned to change to")
                .contains(".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())")
                .contains(".toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm())");
        assertThat(logOutput)
                .as("explains how a tool tells the LLM about a failure under the recommended setting")
                .contains("ToolErrorVisibleToLlm");
        assertThat(logOutput)
                .as("also shows how to keep the current behavior")
                .contains(".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation())")
                .contains(".toolExecutionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm())");
        assertThat(logOutput)
                .contains("https://docs.langchain4j.dev/tutorials/tools#error-handling")
                .contains(ToolErrorHandlingNotice.class.getName());
    }

    @Test
    void should_warn_about_leaking_only_when_the_tool_execution_default_is_used() {

        String logOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .build());

        assertThat(logOutput)
                .as("the tool execution default was chosen explicitly, so nothing is sent to the LLM")
                .doesNotContain("credentials embedded in error messages")
                .doesNotContain(".toolExecutionErrorHandler(");
        assertThat(logOutput).contains(".toolArgumentsErrorHandler(ToolArgumentsErrorHandler.failInvocation())");
    }

    @Test
    void should_log_the_notice_only_once_per_jvm() {

        String firstLogOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .build());
        String secondLogOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .build());

        assertThat(firstLogOutput).contains(Assistant.class.getName());
        assertThat(secondLogOutput).doesNotContain(ToolErrorHandlingNotice.class.getName());
    }

    @Test
    void should_not_warn_when_both_error_handlers_are_configured() {

        String logOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .build());

        assertThat(logOutput).doesNotContain(ToolErrorHandlingNotice.class.getName());
    }

    @Test
    void should_not_warn_when_no_tools_are_configured() {

        AiServiceContext context = AiServiceContext.create(Assistant.class);

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context)).isEmpty();
    }

    @Test
    void should_warn_when_only_a_tool_provider_is_configured() {

        AiServiceContext context = AiServiceContext.create(Assistant.class);
        context.toolService.toolProvider(
                request -> ToolProviderResult.builder().build());

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context))
                .as("tools supplied by a ToolProvider are only known at invocation time, but the defaults still apply")
                .containsExactly(TOOL_ARGUMENTS_ERROR, TOOL_EXECUTION_ERROR);
    }

    @Test
    void should_not_warn_when_all_methods_are_asynchronous() {

        AiServiceContext context = AiServiceContext.create(AsyncAssistant.class);
        context.toolService.tools(List.of(new Tools()));

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context))
                .as("asynchronous and reactive modes already fail on a tool execution error "
                        + "and send a tool arguments error to the LLM")
                .isEmpty();
    }

    @Test
    void should_warn_when_at_least_one_method_is_blocking() {

        AiServiceContext context = AiServiceContext.create(PartiallyAsyncAssistant.class);
        context.toolService.tools(List.of(new Tools()));

        assertThat(ToolErrorHandlingNotice.unconfirmedDefaults(context))
                .containsExactly(TOOL_ARGUMENTS_ERROR, TOOL_EXECUTION_ERROR);
    }

    interface PartiallyAsyncAssistant {

        String chat(String userMessage);

        CompletableFuture<String> chatAsync(String userMessage);
    }

    private static String captureStdErr(Supplier<?> action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.get();
        } finally {
            System.setErr(originalErr);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
