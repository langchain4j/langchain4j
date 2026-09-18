package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * That the notice actually reaches the log when an AI Service is built, and only once.
 * <p>
 * These tests capture {@link System#err} (the default tinylog destination for WARN), so this class runs
 * {@link Isolated} and single-threaded. What the notice says is covered by {@link ToolErrorHandlingNoticeTest},
 * which needs neither.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class ToolErrorHandlingNoticeLoggingTest {

    interface Assistant {

        String chat(String userMessage);
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
    void should_log_the_notice_when_an_ai_service_with_tools_has_no_error_handlers() {

        String logOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .build());

        assertThat(logOutput).contains("WARN").contains(Assistant.class.getName());
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
    void should_not_log_the_notice_when_the_error_handlers_are_configured() {

        String logOutput = captureStdErr(() -> AiServices.builder(Assistant.class)
                .chatModel(CHAT_MODEL)
                .tools(new Tools())
                .toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                .build());

        assertThat(logOutput).doesNotContain(ToolErrorHandlingNotice.class.getName());
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
