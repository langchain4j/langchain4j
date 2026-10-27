package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.StreamingChatModelSupplier;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that a static helper method of a declarative agent returning a type that cannot be used fails the creation of
 * the agent, instead of being ignored or failing at its first use, while a subtype of the expected type, or the
 * wrapper of the expected primitive, is accepted.
 */
class HelperMethodReturnTypeTest {

    static final ChatModelMock MODEL = ChatModelMock.thatAlwaysResponds("ok");

    static final ThreadPoolExecutor EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    static final MessageWindowChatMemory MEMORY = MessageWindowChatMemory.withMaxMessages(10);

    static final AtomicInteger EXIT_CONDITION_CALLS = new AtomicInteger();

    @AfterAll
    static void shutdownExecutor() {
        EXECUTOR.shutdown();
    }

    public static class Echo {

        @Agent(outputKey = "echo")
        public static String echo(@V("text") String text) {
            return text;
        }
    }

    public static class Shout {

        @Agent(outputKey = "shout")
        public static String shout(@V("text") String text) {
            return text.toUpperCase();
        }
    }

    // ── Methods returning a wrong type ──

    public interface ChatModelSupplierReturningStreamingModel {

        @Agent(outputKey = "answer")
        String chat(@V("question") String question);

        @ChatModelSupplier
        static StreamingChatModel chatModel(@V("question") String question) {
            return null;
        }
    }

    public interface StreamingChatModelSupplierReturningChatModel {

        @Agent(outputKey = "answer")
        String chat(@V("question") String question);

        @StreamingChatModelSupplier
        static ChatModel streamingChatModel() {
            return MODEL;
        }
    }

    public interface ToolsSupplierReturningVoid {

        @Agent(outputKey = "answer")
        String chat(@V("question") String question);

        @ToolsSupplier
        static void tools() {}
    }

    public interface McpClientSupplierReturningVoid {

        @McpClientAgent(toolName = "echo", outputKey = "echo")
        String echo(@V("text") String text);

        @McpClientSupplier
        static void mcpClient() {}
    }

    public interface SequenceWithMcpClientSupplierReturningVoid {

        @SequenceAgent(outputKey = "echo", subAgents = McpClientSupplierReturningVoid.class)
        String run(@V("text") String text);
    }

    public interface ParallelExecutorReturningString {

        @ParallelAgent(outputKey = "echo", subAgents = Echo.class)
        String run(@V("text") String text);

        @ParallelExecutor
        static String executor() {
            return "executor";
        }
    }

    public interface SupervisorWithStreamingChatModel {

        @SupervisorAgent(outputKey = "result", subAgents = Echo.class)
        String invoke(@V("request") String request);

        @ChatModelSupplier
        static StreamingChatModel chatModel() {
            return null;
        }
    }

    public interface SupervisorRequestReturningInt {

        @SupervisorAgent(outputKey = "result", subAgents = Echo.class)
        String invoke(@V("request") String request);

        @SupervisorRequest
        static int request(@V("request") String request) {
            return request.length();
        }
    }

    public interface SupervisorChatMemoryProviderReturningString {

        @SupervisorAgent(outputKey = "result", subAgents = Echo.class)
        String invoke(@V("request") String request);

        @ChatMemoryProviderSupplier
        static String chatMemory(Object memoryId) {
            return "memory";
        }
    }

    public interface LoopWithExitConditionReturningString {

        @LoopAgent(outputKey = "echo", maxIterations = 2, subAgents = Echo.class)
        String run(@V("text") String text);

        @ExitCondition
        static String done(@V("echo") String echo) {
            return echo;
        }
    }

    public interface ConditionalWithActivationConditionReturningString {

        @ConditionalAgent(outputKey = "echo", subAgents = Echo.class)
        String run(@V("text") String text);

        @ActivationCondition(Echo.class)
        static String activated(@V("text") String text) {
            return text;
        }
    }

    public interface SequenceWithErrorHandlerReturningString {

        @SequenceAgent(outputKey = "echo", subAgents = Echo.class)
        String run(@V("text") String text);

        @ErrorHandler
        static String recover(ErrorContext errorContext) {
            return "recovered";
        }
    }

    static Stream<Arguments> methodsReturningWrongType() {
        return Stream.of(
                arguments(ChatModelSupplierReturningStreamingModel.class, ChatModel.class),
                arguments(StreamingChatModelSupplierReturningChatModel.class, StreamingChatModel.class),
                arguments(ToolsSupplierReturningVoid.class, Object.class),
                arguments(SequenceWithMcpClientSupplierReturningVoid.class, Object.class),
                arguments(ParallelExecutorReturningString.class, Executor.class),
                arguments(SupervisorWithStreamingChatModel.class, ChatModel.class),
                arguments(SupervisorRequestReturningInt.class, String.class),
                arguments(SupervisorChatMemoryProviderReturningString.class, ChatMemory.class),
                arguments(LoopWithExitConditionReturningString.class, boolean.class),
                arguments(ConditionalWithActivationConditionReturningString.class, boolean.class),
                arguments(SequenceWithErrorHandlerReturningString.class, ErrorRecoveryResult.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("methodsReturningWrongType")
    void method_returning_wrong_type_fails_agent_creation(Class<?> agentClass, Class<?> expectedType) {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(agentClass, MODEL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return " + expectedType.getName());
    }

    public interface SequenceWithChatModelName {

        @SequenceAgent(outputKey = "echo", subAgents = Echo.class)
        String run(@V("text") String text);

        @ChatModelSupplier
        static String chatModel() {
            return "gpt-5";
        }
    }

    @Test
    void root_chat_model_supplier_returning_wrong_type_fails_agent_creation() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(SequenceWithChatModelName.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return " + ChatModel.class.getName());
    }

    // ── Methods returning a subtype of the expected type ──

    public interface ParallelWithExecutorService {

        // a single subagent would be invoked without the executor
        @ParallelAgent(
                outputKey = "echo",
                subAgents = {Echo.class, Shout.class})
        String run(@V("text") String text);

        @ParallelExecutor
        static ExecutorService executor() {
            return EXECUTOR;
        }
    }

    @Test
    void parallel_executor_returning_a_subtype_is_used() {
        ParallelWithExecutorService agent = AgenticServices.createAgenticSystem(ParallelWithExecutorService.class);

        agent.run("hello");

        assertThat(EXECUTOR.getTaskCount()).isPositive();
    }

    public interface LoopWithBooleanExitCondition {

        @LoopAgent(outputKey = "echo", maxIterations = 5, subAgents = Echo.class)
        String run(@V("text") String text);

        @ExitCondition
        static Boolean done(@V("echo") String echo) {
            EXIT_CONDITION_CALLS.incrementAndGet();
            return true;
        }
    }

    @Test
    void exit_condition_returning_boolean_wrapper_is_used() {
        LoopWithBooleanExitCondition loop = AgenticServices.createAgenticSystem(LoopWithBooleanExitCondition.class);

        assertThat(loop.run("hello")).isEqualTo("hello");
        assertThat(EXIT_CONDITION_CALLS).hasPositiveValue();
    }

    public interface AssistantWithMessageWindowMemory {

        @UserMessage("{{question}}")
        @Agent(outputKey = "answer")
        String chat(@V("question") String question);

        @ChatMemorySupplier
        static MessageWindowChatMemory chatMemory() {
            return MEMORY;
        }
    }

    @Test
    void chat_memory_supplier_returning_a_subtype_is_used() {
        AssistantWithMessageWindowMemory assistant =
                AgenticServices.createAgenticSystem(AssistantWithMessageWindowMemory.class, MODEL);

        assertThat(assistant.chat("hello")).isEqualTo("ok");
        assertThat(MEMORY.messages()).isNotEmpty();
    }
}
