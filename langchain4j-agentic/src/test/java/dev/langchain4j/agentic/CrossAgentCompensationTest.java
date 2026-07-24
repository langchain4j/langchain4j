package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

public class CrossAgentCompensationTest {

    static final List<String> compensationLog = Collections.synchronizedList(new ArrayList<>());

    static ChatModel modelThatCallsTool(String toolName, String toolArgs) {
        Queue<AiMessage> responses = new ConcurrentLinkedQueue<>();
        responses.add(AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name(toolName)
                .arguments(toolArgs)
                .build()));
        responses.add(AiMessage.from("done"));
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                        .aiMessage(responses.isEmpty() ? AiMessage.from("done") : responses.poll())
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };
    }

    static ChatModel throwingModel() {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new RuntimeException("Agent failed");
            }
        };
    }

    // ----- Tool classes -----

    static class CreditService {
        boolean credited = false;
        boolean compensated = false;

        @Tool("credits an account")
        String credit(@P(name = "amount") int amount) {
            credited = true;
            compensationLog.add("credit:" + amount);
            return "credited " + amount;
        }

        @CompensateFor("credit")
        void uncredit(int amount) {
            compensated = true;
            compensationLog.add("uncredit:" + amount);
        }
    }

    static class DebitService {
        boolean debited = false;
        boolean compensated = false;

        @Tool("debits an account")
        String debit(@P(name = "amount") int amount) {
            debited = true;
            compensationLog.add("debit:" + amount);
            return "debited " + amount;
        }

        @CompensateFor("debit")
        void undebit(int amount) {
            compensated = true;
            compensationLog.add("undebit:" + amount);
        }
    }

    static class ThrowingCompensationService {
        boolean compensated = false;

        @Tool("does something")
        String doSomething(@P(name = "input") String input) {
            compensationLog.add("doSomething:" + input);
            return "done " + input;
        }

        @CompensateFor("doSomething")
        void undoSomething(String input) {
            compensationLog.add("undoSomething:" + input);
            compensated = true;
            throw new RuntimeException("Compensation failed");
        }
    }

    static class NoCompensationToolService {
        @Tool("tool without compensation")
        String noCompensation(@P(name = "input") String input) {
            compensationLog.add("noCompensation:" + input);
            return "done";
        }
    }

    // ----- Agent interfaces -----

    public interface CreditAgentService {
        @Agent(description = "Credits an account", outputKey = "creditResult")
        @UserMessage("Credit the account for request: {{request}}")
        String execute(@V("request") String request);
    }

    public interface DebitAgentService {
        @Agent(description = "Debits an account", outputKey = "debitResult")
        @UserMessage("Debit the account for request: {{request}}")
        String execute(@V("request") String request);
    }

    public interface FailingAgentService {
        @Agent(description = "An agent that fails", outputKey = "failResult")
        @UserMessage("Do something with {{request}}")
        String execute(@V("request") String request);
    }

    public interface SimpleAgentService {
        @Agent(description = "A simple agent", outputKey = "simpleResult")
        @UserMessage("Process {{request}}")
        String execute(@V("request") String request);
    }

    public interface NoCompAgentService {
        @Agent(description = "Agent without compensation tools", outputKey = "noCompResult")
        @UserMessage("Process {{request}}")
        String execute(@V("request") String request);
    }

    // ----- Tests -----

    @Test
    void should_compensate_all_tools_in_sequence_when_last_agent_fails() {
        compensationLog.clear();
        CreditService creditService = new CreditService();
        DebitService debitService = new DebitService();

        var creditAgent = AgenticServices.agentBuilder(CreditAgentService.class)
                .chatModel(modelThatCallsTool("credit", "{\"amount\": 100}"))
                .tools(creditService)
                .name("creditAgent")
                .build();

        var debitAgent = AgenticServices.agentBuilder(DebitAgentService.class)
                .chatModel(modelThatCallsTool("debit", "{\"amount\": 100}"))
                .tools(debitService)
                .name("debitAgent")
                .build();

        var failAgent = AgenticServices.agentBuilder(FailingAgentService.class)
                .chatModel(throwingModel())
                .name("failAgent")
                .build();

        interface TestAgent {
            String run(@V("request") String request);
        }

        TestAgent sequenceAgent = AgenticServices.<TestAgent>sequenceBuilder(TestAgent.class)
                .subAgents(creditAgent, debitAgent, failAgent)
                .compensateOnError(true)
                .name("sequenceAgent")
                .build();

        assertThrows(AgentInvocationException.class, () -> sequenceAgent.run("test"));

        assertThat(creditService.credited).isTrue();
        assertThat(debitService.debited).isTrue();
        assertThat(creditService.compensated).isTrue();
        assertThat(debitService.compensated).isTrue();

        int uncreditIdx = compensationLog.indexOf("uncredit:100");
        int undebitIdx = compensationLog.indexOf("undebit:100");
        assertThat(undebitIdx).isLessThan(uncreditIdx);
    }

    @Test
    void should_not_compensate_when_flag_is_not_set() {
        compensationLog.clear();
        CreditService creditService = new CreditService();

        var creditAgent = AgenticServices.agentBuilder(CreditAgentService.class)
                .chatModel(modelThatCallsTool("credit", "{\"amount\": 100}"))
                .tools(creditService)
                .name("creditAgent")
                .build();

        var failAgent = AgenticServices.agentBuilder(FailingAgentService.class)
                .chatModel(throwingModel())
                .name("failAgent")
                .build();

        interface TestAgent {
            String run(@V("request") String request);
        }

        TestAgent sequenceAgent = AgenticServices.<TestAgent>sequenceBuilder(TestAgent.class)
                .subAgents(creditAgent, failAgent)
                .name("sequenceAgent")
                .build();

        assertThrows(AgentInvocationException.class, () -> sequenceAgent.run("test"));

        assertThat(creditService.credited).isTrue();
        assertThat(creditService.compensated).isFalse();
    }

    @Test
    void should_only_compensate_tools_with_compensateFor_annotation() {
        compensationLog.clear();
        CreditService creditService = new CreditService();
        NoCompensationToolService noCompService = new NoCompensationToolService();

        var creditAgent = AgenticServices.agentBuilder(CreditAgentService.class)
                .chatModel(modelThatCallsTool("credit", "{\"amount\": 100}"))
                .tools(creditService)
                .name("creditAgent")
                .build();

        var noCompAgent = AgenticServices.agentBuilder(NoCompAgentService.class)
                .chatModel(modelThatCallsTool("noCompensation", "{\"input\": \"test\"}"))
                .tools(noCompService)
                .name("noCompAgent")
                .build();

        var failAgent = AgenticServices.agentBuilder(FailingAgentService.class)
                .chatModel(throwingModel())
                .name("failAgent")
                .build();

        interface TestAgent {
            String run(@V("request") String request);
        }

        TestAgent sequenceAgent = AgenticServices.<TestAgent>sequenceBuilder(TestAgent.class)
                .subAgents(creditAgent, noCompAgent, failAgent)
                .compensateOnError(true)
                .name("sequenceAgent")
                .build();

        assertThrows(AgentInvocationException.class, () -> sequenceAgent.run("test"));

        assertThat(creditService.compensated).isTrue();
        assertThat(compensationLog).containsExactly("credit:100", "noCompensation:test", "uncredit:100");
    }

    @Test
    void should_continue_compensating_when_one_compensation_fails() {
        compensationLog.clear();
        ThrowingCompensationService throwingCompService = new ThrowingCompensationService();
        CreditService creditService = new CreditService();

        var throwingCompAgent = AgenticServices.agentBuilder(SimpleAgentService.class)
                .chatModel(modelThatCallsTool("doSomething", "{\"input\": \"test\"}"))
                .tools(throwingCompService)
                .name("throwingCompAgent")
                .build();

        var creditAgent = AgenticServices.agentBuilder(CreditAgentService.class)
                .chatModel(modelThatCallsTool("credit", "{\"amount\": 50}"))
                .tools(creditService)
                .name("creditAgent")
                .build();

        var failAgent = AgenticServices.agentBuilder(FailingAgentService.class)
                .chatModel(throwingModel())
                .name("failAgent")
                .build();

        interface TestAgent {
            String run(@V("request") String request);
        }

        TestAgent sequenceAgent = AgenticServices.<TestAgent>sequenceBuilder(TestAgent.class)
                .subAgents(throwingCompAgent, creditAgent, failAgent)
                .compensateOnError(true)
                .name("sequenceAgent")
                .build();

        assertThrows(AgentInvocationException.class, () -> sequenceAgent.run("test"));

        // Both compensations attempted even though the first throws
        assertThat(throwingCompService.compensated).isTrue();
        assertThat(creditService.compensated).isTrue();
    }

    @Test
    void should_compensate_in_reverse_chronological_order() {
        compensationLog.clear();
        CreditService creditService = new CreditService();
        DebitService debitService = new DebitService();

        var creditAgent = AgenticServices.agentBuilder(CreditAgentService.class)
                .chatModel(modelThatCallsTool("credit", "{\"amount\": 200}"))
                .tools(creditService)
                .name("creditAgent")
                .build();

        var debitAgent = AgenticServices.agentBuilder(DebitAgentService.class)
                .chatModel(modelThatCallsTool("debit", "{\"amount\": 300}"))
                .tools(debitService)
                .name("debitAgent")
                .build();

        var failAgent = AgenticServices.agentBuilder(FailingAgentService.class)
                .chatModel(throwingModel())
                .name("failAgent")
                .build();

        interface TestAgent {
            String run(@V("request") String request);
        }

        TestAgent sequenceAgent = AgenticServices.<TestAgent>sequenceBuilder(TestAgent.class)
                .subAgents(creditAgent, debitAgent, failAgent)
                .compensateOnError(true)
                .name("sequenceAgent")
                .build();

        assertThrows(AgentInvocationException.class, () -> sequenceAgent.run("test"));

        assertThat(compensationLog).containsExactly(
                "credit:200", "debit:300", "undebit:300", "uncredit:200");
    }

    // ----- Declarative annotation test -----

    public interface DeclarativeCreditAgent {
        @Agent(description = "Credits an account", outputKey = "creditResult")
        @UserMessage("Credit the account for request: {{request}}")
        String execute(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return modelThatCallsTool("credit", "{\"amount\": 100}");
        }
    }

    public interface DeclarativeFailingAgent {
        @Agent(description = "A failing agent", outputKey = "failResult")
        @UserMessage("Do something with {{request}}")
        String execute(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return throwingModel();
        }
    }

    public interface DeclarativeCompensatingSequence {
        @SequenceAgent(
                compensateOnError = true,
                outputKey = "result",
                subAgents = {DeclarativeCreditAgent.class, DeclarativeFailingAgent.class})
        String run(@V("request") String request);
    }

    @Test
    void should_compensate_with_declarative_annotation() {
        compensationLog.clear();
        CreditService creditService = new CreditService();

        DeclarativeCompensatingSequence agent = AgenticServices.createAgenticSystem(
                DeclarativeCompensatingSequence.class,
                new AgentConfigurator(ctx -> {
                    if (ctx.agentServiceClass() == DeclarativeCreditAgent.class) {
                        ctx.agentBuilder().tools(creditService);
                    }
                }, null, null));

        assertThrows(AgentInvocationException.class, () -> agent.run("test"));

        assertThat(creditService.credited).isTrue();
        assertThat(creditService.compensated).isTrue();
    }
}
