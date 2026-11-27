package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * A result without text cannot carry the note that an input guardrail changed the arguments, so the LLM is not told:
 * that is logged at WARN, once per tool.
 * <p>
 * These tests capture {@link System#err} (the default tinylog destination for WARN), so this class runs
 * {@link Isolated} and single-threaded.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class ToolArgumentsRewriteNoteWarningTest {

    private static final String WARNING = "so the LLM is not told that an input guardrail changed the arguments";

    interface Assistant {

        String chat(@UserMessage String message);
    }

    private static final ToolExecutor IMAGE_ONLY = new ToolExecutor() {
        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            return ToolExecutionResult.builder()
                    .resultContents(List.of(ImageContent.from("http://example.com/receipt.png")))
                    .build();
        }
    };

    private static ToolExecutionRequest receiptCall(String id) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("receipt")
                .arguments("{\"amount\":10000}")
                .build();
    }

    private static Assistant assistant(ToolInputGuardrail guardrail) {
        return AiServices.builder(Assistant.class)
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(receiptCall("1")),
                        AiMessage.from(receiptCall("2")),
                        AiMessage.from("done")))
                .tools(List.<AiServiceTool>of(AiServiceTool.builder()
                        .toolSpecification(ToolSpecification.builder().name("receipt").build())
                        .toolExecutor(IMAGE_ONLY)
                        .build()))
                .toolInputGuardrails(guardrail)
                .build();
    }

    @Test
    void a_result_without_text_that_misses_the_note_is_logged_once_per_tool() {
        ToolInputGuardrail capping = request -> ToolInputGuardrailResult.successWithArguments(Map.of("amount", 1000));

        var log = captureStdErr(() -> assistant(capping).chat("receipt"));

        assertThat(log).contains("WARN").contains("'receipt'");
        assertThat(log.split(WARNING, -1)).hasSize(2);
    }

    @Test
    void a_result_without_text_and_without_a_rewrite_is_not_logged() {
        ToolInputGuardrail accepting = request -> ToolInputGuardrailResult.success();

        var log = captureStdErr(() -> assistant(accepting).chat("receipt"));

        assertThat(log).doesNotContain(WARNING);
    }

    private static String captureStdErr(Runnable action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(originalErr);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
