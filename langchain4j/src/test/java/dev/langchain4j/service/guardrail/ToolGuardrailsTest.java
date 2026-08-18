package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Tests run concurrently in this module, so everything the tools and guardrails record lives in
 * per-test instances rather than in static state.
 */
class ToolGuardrailsTest {

    interface Assistant {
        String chat(String userMessage);
    }

    interface StreamingAssistant {
        TokenStream chat(String userMessage);
    }

    /** What the tools actually did, so a refused call can be told apart from an executed one. */
    private final List<String> executed = new ArrayList<>();

    // This module is compiled without -parameters, so tool parameters are named explicitly.
    public static class WikiTools {

        private final List<String> executed;

        public WikiTools(List<String> executed) {
            this.executed = executed;
        }

        @Tool("Writes a page")
        String writePage(@P(name = "pageId") String pageId, @P(name = "content") String content) {
            executed.add(pageId + "=" + content);
            return "Wrote " + pageId;
        }

        @Tool("Reads a page")
        String readPage(@P(name = "pageId") String pageId) {
            executed.add("read " + pageId);
            return "secret token abc123";
        }
    }

    // ---------------------------------------------------------------- guardrails under test

    /**
     * The policy a ToolProvider cannot express: whether the call is allowed depends on an argument,
     * which only exists once the model has actually asked for the call.
     */
    public static class ImmutableSources implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            Object pageId = request.argumentsAsMap().get("pageId");
            return pageId instanceof String id && id.startsWith("raw/")
                    ? failure("raw/ is read-only. Write under pages/ instead.")
                    : success();
        }
    }

    public static class RewriteToPagesPrefix implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return successWith(request.executionRequest().toBuilder()
                    .arguments("{\"pageId\":\"pages/rewritten.md\",\"content\":\"c\"}")
                    .build());
        }
    }

    public static class NeverAllowed implements ToolInputGuardrail {
        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return fatal("Tool calling is disabled for this tenant.");
        }
    }

    public static class RedactSecrets implements ToolOutputGuardrail {
        @Override
        public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
            return successWith(ToolExecutionResult.builder()
                    .resultText(request.resultText().replace("abc123", "[REDACTED]"))
                    .build());
        }
    }

    public static class RecordingGuardrail implements ToolInputGuardrail {

        private final List<String> seen;

        public RecordingGuardrail(List<String> seen) {
            this.seen = seen;
        }

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            seen.add(request.toolName() + " " + request.arguments());
            return success();
        }
    }

    // ---------------------------------------------------------------- annotated tools

    public static class MethodAnnotatedTools {

        private final List<String> executed;

        public MethodAnnotatedTools(List<String> executed) {
            this.executed = executed;
        }

        @Tool("Writes a page")
        @ToolInputGuardrails(ImmutableSources.class)
        String writePage(@P(name = "pageId") String pageId, @P(name = "content") String content) {
            executed.add(pageId + "=" + content);
            return "Wrote " + pageId;
        }
    }

    @ToolInputGuardrails(ImmutableSources.class)
    public static class ClassAnnotatedTools {

        private final List<String> executed;

        public ClassAnnotatedTools(List<String> executed) {
            this.executed = executed;
        }

        @Tool("Writes a page")
        String writePage(@P(name = "pageId") String pageId, @P(name = "content") String content) {
            executed.add(pageId + "=" + content);
            return "Wrote " + pageId;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static ChatModelMock modelCalling(String toolName, String arguments) {
        return ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name(toolName)
                        .arguments(arguments)
                        .build()),
                AiMessage.from("Done"));
    }

    private static ChatModelMock modelWriting(String pageId) {
        return modelCalling("writePage", "{\"pageId\":\"%s\",\"content\":\"x\"}".formatted(pageId));
    }

    private static List<String> toolResultsSeenBy(ChatModelMock model) {
        return model.requests().stream()
                .flatMap(request -> request.messages().stream())
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ChatMessage::toString)
                .toList();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void refused_call_does_not_execute_the_tool() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new ImmutableSources())
                .build();

        assertThat(assistant.chat("annotate the source")).isEqualTo("Done");
        assertThat(executed).isEmpty();
    }

    @Test
    void the_refusal_is_handed_back_to_the_llm_so_it_can_adapt() {
        ChatModelMock model = modelWriting("raw/article.md");

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new ImmutableSources())
                .build()
                .chat("annotate the source");

        assertThat(toolResultsSeenBy(model)).singleElement().asString().contains("raw/ is read-only");
    }

    @Test
    void allowed_call_executes_normally() {
        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("pages/memex.md"))
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new ImmutableSources())
                .build()
                .chat("write it up");

        assertThat(executed).containsExactly("pages/memex.md=x");
    }

    @Test
    void guardrail_can_rewrite_the_arguments_the_tool_is_called_with() {
        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new RewriteToPagesPrefix())
                .build()
                .chat("write it up");

        assertThat(executed).containsExactly("pages/rewritten.md=c");
    }

    @Test
    void guardrails_chain_and_each_sees_what_the_previous_one_rewrote() {
        List<String> seen = new ArrayList<>();

        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new RewriteToPagesPrefix(), new RecordingGuardrail(seen))
                .build()
                .chat("write it up");

        assertThat(seen).singleElement().asString().contains("pages/rewritten.md");
        assertThat(executed).containsExactly("pages/rewritten.md=c");
    }

    @Test
    void fatal_failure_aborts_the_invocation() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(modelWriting("pages/memex.md"))
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new NeverAllowed())
                .build();

        assertThatExceptionOfType(ToolGuardrailException.class)
                .isThrownBy(() -> assistant.chat("write it up"))
                .withMessageContaining("Tool calling is disabled");

        assertThat(executed).isEmpty();
    }

    @Test
    void output_guardrail_rewrites_what_the_llm_sees() {
        ChatModelMock model = modelCalling("readPage", "{\"pageId\":\"pages/memex.md\"}");

        AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new WikiTools(executed))
                .toolOutputGuardrails(new RedactSecrets())
                .build()
                .chat("read it");

        assertThat(executed).containsExactly("read pages/memex.md");
        assertThat(toolResultsSeenBy(model))
                .singleElement()
                .asString()
                .contains("[REDACTED]")
                .doesNotContain("abc123");
    }

    @Test
    void method_level_annotation_guards_that_tool() {
        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .tools(new MethodAnnotatedTools(executed))
                .build()
                .chat("annotate the source");

        assertThat(executed).isEmpty();
    }

    @Test
    void class_level_annotation_guards_every_tool_of_the_class() {
        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .tools(new ClassAnnotatedTools(executed))
                .build()
                .chat("annotate the source");

        assertThat(executed).isEmpty();
    }

    @Test
    void guardrails_configured_before_tools_are_still_applied() {
        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .toolInputGuardrails(new ImmutableSources()) // configured *before* .tools(...)
                .tools(new WikiTools(executed))
                .build()
                .chat("annotate the source");

        assertThat(executed).isEmpty();
    }

    /**
     * Streaming executes tools through the same {@code ToolService} choke point, so guardrails have to
     * apply there too. If they ever stopped applying, the guarantee would silently disappear for every
     * streaming AI Service.
     */
    @Test
    void guardrails_apply_to_streaming_ai_services_too() throws Exception {
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("writePage")
                        .arguments("{\"pageId\":\"raw/article.md\",\"content\":\"x\"}")
                        .build()),
                AiMessage.from("Done"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(new WikiTools(executed))
                .toolInputGuardrails(new ImmutableSources())
                .build();

        CompletableFuture<String> answer = new CompletableFuture<>();
        assistant.chat("annotate the source")
                .onCompleteResponse(response -> answer.complete(response.aiMessage().text()))
                .onError(answer::completeExceptionally)
                .start();

        assertThat(answer.get(30, TimeUnit.SECONDS)).isEqualTo("Done");
        assertThat(executed).isEmpty();
    }

    @Test
    void tools_without_guardrails_are_untouched() {
        AiServices.builder(Assistant.class)
                .chatModel(modelWriting("raw/article.md"))
                .tools(new WikiTools(executed))
                .build()
                .chat("annotate the source");

        assertThat(executed).containsExactly("raw/article.md=x");
    }
}
