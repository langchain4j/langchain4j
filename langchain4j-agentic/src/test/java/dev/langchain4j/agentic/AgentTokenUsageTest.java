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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
}
