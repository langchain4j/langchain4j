package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AgentTokenUsageTest {

    public interface Assistant {

        @UserMessage("Answer {{question}}")
        @Agent(outputKey = "answer")
        String answer(@V("question") String question);
    }

    public interface Workflow {

        @Agent
        String run(@V("question") String question);
    }

    public interface MemoryAssistant {

        @UserMessage("Answer {{question}}")
        @Agent(outputKey = "answer")
        String answer(@MemoryId Object memoryId, @V("question") String question);
    }

    public static class Clock {

        @Tool
        public String time() {
            return "noon";
        }
    }

    private static ChatModel modelCallingToolBeforeAnswering() {
        AtomicInteger calls = new AtomicInteger();
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                if (calls.getAndIncrement() % 2 == 0) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                    .id("1")
                                    .name("time")
                                    .arguments("{}")
                                    .build()))
                            .tokenUsage(new TokenUsage(100, 20))
                            .build();
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("It is noon"))
                        .tokenUsage(new TokenUsage(30, 5))
                        .build();
            }
        };
    }

    private static ChatModel modelAnsweringDirectly() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("It is noon"))
                        .tokenUsage(new TokenUsage(30, 5))
                        .build();
            }
        };
    }

    private static Assistant assistantWithTool() {
        return AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelCallingToolBeforeAnswering())
                .tools(new Clock())
                .build();
    }

    private static List<AgentInvocation> nestedInvocations(AgentMonitor monitor) {
        return monitor.successfulExecutions().get(0).topLevelInvocations().nestedInvocations();
    }

    @Test
    void agent_reports_the_tokens_of_every_model_call_of_its_tool_loop() {
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistantWithTool())
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        AgentInvocation invocation = nestedInvocations(monitor).get(0);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.totalTokenCount()).isEqualTo(155);
    }

    private static Assistant assistantWithToolUsingModels(String toolCallingModel, String answeringModel) {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("time")
                                .arguments("{}")
                                .build()),
                        AiMessage.from("It is noon"))
                .withModelNames(toolCallingModel, answeringModel)
                .withTokenUsages(new TokenUsage(100, 20), new TokenUsage(30, 5));
        return AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .tools(new Clock())
                .build();
    }

    @Test
    void agent_whose_model_calls_used_different_models_does_not_attribute_its_total_tokens_to_one_model() {
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistantWithToolUsingModels("tool-calling-model", "answering-model"))
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        AgentInvocation invocation = nestedInvocations(monitor).get(0);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.modelName()).isEmpty();
    }

    @Test
    void agent_whose_model_calls_used_the_same_model_reports_that_model_with_its_total_tokens() {
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistantWithToolUsingModels("answering-model", "answering-model"))
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        AgentInvocation invocation = nestedInvocations(monitor).get(0);
        assertThat(invocation.tokenUsage()).contains(new TokenUsage(130, 25));
        assertThat(invocation.modelName()).contains("answering-model");
    }

    @Test
    void agent_invoked_twice_reports_the_tokens_of_each_invocation_separately() {
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.loopBuilder(Workflow.class)
                .subAgents(assistantWithTool())
                .maxIterations(2)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        assertThat(nestedInvocations(monitor))
                .extracting(AgentInvocation::totalTokenCount)
                .containsExactly(155, 155);
    }

    @Test
    void retried_agent_reports_only_the_tokens_of_the_invocation_that_succeeded() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel modelFailingOnce = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                int call = calls.getAndIncrement();
                if (call == 1) {
                    throw new RuntimeException("model unavailable");
                }
                if (call % 2 == 0) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                    .id("1")
                                    .name("time")
                                    .arguments("{}")
                                    .build()))
                            .tokenUsage(new TokenUsage(100, 20))
                            .build();
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("It is noon"))
                        .tokenUsage(new TokenUsage(30, 5))
                        .build();
            }
        };
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelFailingOnce)
                .tools(new Clock())
                .build();
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .errorHandler(errorContext -> ErrorRecoveryResult.retry())
                .listener(monitor)
                .build()
                .run("what time is it?");

        List<AgentInvocation> invocations = nestedInvocations(monitor);
        assertThat(invocations.get(invocations.size() - 1).totalTokenCount()).isEqualTo(155);
    }

    @Test
    void last_chat_response_keeps_the_final_answer_with_the_total_token_usage() {
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        AgentListener listener = new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                responses.add(agentResponse);
            }

            @Override
            public boolean inheritedBySubagents() {
                return true;
            }
        };
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistantWithTool())
                .outputKey("answer")
                .listener(listener)
                .build()
                .run("what time is it?");

        ChatResponse chatResponse = responses.get(0).chatResponse();
        assertThat(chatResponse.aiMessage().text()).isEqualTo("It is noon");
        assertThat(chatResponse.tokenUsage()).isEqualTo(new TokenUsage(130, 25));
    }

    @Test
    void agent_without_tools_reports_the_tokens_of_its_single_model_call() {
        AgentMonitor monitor = new AgentMonitor();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelAnsweringDirectly())
                .build();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        assertThat(nestedInvocations(monitor).get(0).totalTokenCount()).isEqualTo(35);
    }

    @Test
    void standalone_agent_reports_its_token_usage() {
        AgentMonitor monitor = new AgentMonitor();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelCallingToolBeforeAnswering())
                .tools(new Clock())
                .listener(monitor)
                .build();

        assistant.answer("what time is it?");

        assertThat(monitor.successfulExecutions().get(0).topLevelInvocations().totalTokenCount())
                .isEqualTo(155);
    }

    @Test
    void standalone_agent_does_not_keep_its_last_response() {
        AgentMonitor monitor = new AgentMonitor();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelAnsweringDirectly())
                .listener(monitor)
                .build();

        assistant.answer("what time is it?");

        Object memoryId = monitor.successfulExecutions().get(0).memoryId();
        assertThat(((ChatMessagesAccess) assistant).lastChatResponse(memoryId)).isNull();
    }

    @Test
    void failed_agent_does_not_keep_the_response_of_its_last_successful_model_call() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel modelFailingAfterToolCall = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                if (calls.getAndIncrement() == 0) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                                    .id("1")
                                    .name("time")
                                    .arguments("{}")
                                    .build()))
                            .tokenUsage(new TokenUsage(100, 20))
                            .build();
                }
                throw new RuntimeException("model unavailable");
            }
        };
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelFailingAfterToolCall)
                .tools(new Clock())
                .build();
        AgentMonitor monitor = new AgentMonitor();
        Workflow workflow = AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .listener(monitor)
                .build();

        assertThatThrownBy(() -> workflow.run("what time is it?")).isInstanceOf(AgentInvocationException.class);

        Object memoryId = monitor.failedExecutions().get(0).memoryId();
        assertThat(((ChatMessagesAccess) assistant).lastChatResponse(memoryId)).isNull();
    }

    @Test
    void agent_without_output_does_not_keep_its_last_response() {
        ChatModel modelReturningNoText = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().build())
                        .tokenUsage(new TokenUsage(30, 5))
                        .build();
            }
        };
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelReturningNoText)
                .build();
        AgentMonitor monitor = new AgentMonitor();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        Object memoryId = monitor.successfulExecutions().get(0).memoryId();
        assertThat(((ChatMessagesAccess) assistant).lastChatResponse(memoryId)).isNull();
    }

    @Test
    void response_cleanup_does_not_clear_the_underlying_chat_memory_messages() {
        AgentMonitor monitor = new AgentMonitor();
        Map<Object, MessageWindowChatMemory> suppliedMemories = new ConcurrentHashMap<>();
        MemoryAssistant assistant = AgenticServices.agentBuilder(MemoryAssistant.class)
                .chatModel(modelAnsweringDirectly())
                .chatMemoryProvider(memoryId -> {
                    MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                            .id(memoryId)
                            .maxMessages(10)
                            .build();
                    suppliedMemories.put(memoryId, memory);
                    return memory;
                })
                .build();
        AgenticServices.sequenceBuilder(Workflow.class)
                .subAgents(assistant)
                .outputKey("answer")
                .listener(monitor)
                .build()
                .run("what time is it?");

        Object memoryId = monitor.successfulExecutions().get(0).memoryId();
        assertThat(nestedInvocations(monitor).get(0).totalTokenCount()).isEqualTo(35);
        assertThat(suppliedMemories.get(memoryId).messages()).hasSize(2).last().isEqualTo(AiMessage.from("It is noon"));
        assertThat(((ChatMessagesAccess) assistant).lastChatRequest(memoryId)).isNull();
        assertThat(((ChatMessagesAccess) assistant).lastChatResponse(memoryId)).isNull();
    }

    @Test
    void standalone_listener_receives_the_request_after_the_cached_entry_is_released() {
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(modelAnsweringDirectly())
                .listener(new AgentListener() {
                    @Override
                    public void afterAgentInvocation(AgentResponse response) {
                        responses.add(response);
                    }
                })
                .build();

        for (int i = 0; i < 10; i++) {
            assistant.answer("question " + i);
        }

        assertThat(responses).hasSize(10);
        assertThat(responses)
                .extracting(response -> response.agenticScope().memoryId())
                .doesNotHaveDuplicates();
        for (int i = 0; i < responses.size(); i++) {
            AgentResponse response = responses.get(i);
            assertThat(response.inputs()).containsEntry("question", "question " + i);
            assertThat(response.chatRequest().messages())
                    .contains(dev.langchain4j.data.message.UserMessage.from("Answer question " + i));
            assertThat(response.chatResponse().aiMessage()).isEqualTo(AiMessage.from("It is noon"));
            assertThat(response.chatResponse().tokenUsage()).isEqualTo(new TokenUsage(30, 5));
            assertThat(((ChatMessagesAccess) assistant)
                            .lastChatRequest(response.agenticScope().memoryId()))
                    .isNull();
            assertThat(((ChatMessagesAccess) assistant)
                            .lastChatResponse(response.agenticScope().memoryId()))
                    .isNull();
        }
    }

    @Test
    void concurrent_standalone_calls_keep_their_responses_separate_and_release_them() throws Exception {
        CyclicBarrier bothInModel = new CyclicBarrier(2);
        List<AgentResponse> responses = new CopyOnWriteArrayList<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                String question = ((dev.langchain4j.data.message.UserMessage)
                                request.messages().get(request.messages().size() - 1))
                        .singleText();
                try {
                    bothInModel.await(10, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(question))
                        .tokenUsage(question.endsWith("first") ? new TokenUsage(11, 2) : new TokenUsage(20, 3))
                        .build();
            }
        };
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(model)
                .listener(new AgentListener() {
                    @Override
                    public void afterAgentInvocation(AgentResponse response) {
                        responses.add(response);
                    }
                })
                .build();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                String answer = assistant.answer("first");
                assertThat(LangChain4jManaged.current(AgenticScope.class)).isNull();
                return answer;
            });
            var second = executor.submit(() -> {
                String answer = assistant.answer("second");
                assertThat(LangChain4jManaged.current(AgenticScope.class)).isNull();
                return answer;
            });
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo("Answer first");
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo("Answer second");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(response -> response.agenticScope().memoryId())
                .doesNotHaveDuplicates();
        for (AgentResponse response : responses) {
            boolean first = "first".equals(response.inputs().get("question"));
            assertThat(response.chatResponse().aiMessage().text()).isEqualTo(first ? "Answer first" : "Answer second");
            assertThat(response.chatResponse().tokenUsage())
                    .isEqualTo(first ? new TokenUsage(11, 2) : new TokenUsage(20, 3));
            assertThat(((ChatMessagesAccess) assistant)
                            .lastChatResponse(response.agenticScope().memoryId()))
                    .isNull();
        }
    }
}
