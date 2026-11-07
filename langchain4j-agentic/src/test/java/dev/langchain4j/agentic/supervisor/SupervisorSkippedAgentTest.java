package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupervisorSkippedAgentTest {

    @Test
    void supervisor_is_told_why_an_agent_was_skipped() {
        ChatModelMock plannerModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("""
                {"agentName": "bookFlight", "arguments": {"destination": "Rome"}}
                """),
                AiMessage.from("""
                {"agentName": "done", "arguments": {"response": "no flight booked"}}
                """));
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        AgenticScopeFunction<String> bookFlight = AgenticServices.nonAiAgentBuilder(scope -> {
                    invocations.incrementAndGet();
                    return "stale booking";
                })
                .name("bookFlight")
                .description("Books a flight")
                .inputKey(String.class, "destination")
                .outputKey("booking")
                // holds when the agent is offered to the model, but no more when it is invoked
                .activationCondition("the budget is approved", scope -> evaluations.incrementAndGet() == 1)
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(bookFlight)
                .build();

        String result = supervisor.invoke("Book a flight to Rome");

        assertThat(result).isEqualTo("no flight booked");
        assertThat(invocations).hasValue(0);
        List<String> userMessages = plannerModel.requests().stream()
                .map(request -> ((UserMessage) request.messages().get(request.messages().size() - 1)).singleText())
                .toList();
        assertThat(userMessages)
                .hasSize(2)
                .last()
                .asString()
                .contains("The last received response is: 'agent 'bookFlight' was skipped: "
                        + "activation condition is false: the budget is approved'");
    }

    @Test
    void supervisor_offers_only_the_agents_whose_condition_holds() {
        ChatModelMock plannerModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("""
                {"agentName": "approveBudget", "arguments": {}}
                """),
                AiMessage.from("""
                {"agentName": "bookFlight", "arguments": {"destination": "Rome"}}
                """),
                AiMessage.from("""
                {"agentName": "done", "arguments": {"response": "flight booked"}}
                """));
        AgenticScopeFunction<Boolean> approveBudget = AgenticServices.nonAiAgentBuilder(scope -> true)
                .name("approveBudget")
                .description("Approves the budget")
                .outputKey("approved")
                .build();
        AgenticScopeFunction<String> bookFlight = AgenticServices.nonAiAgentBuilder(scope -> "booked")
                .name("bookFlight")
                .description("Books a flight")
                .inputKey(String.class, "destination")
                .outputKey("booking")
                .activationCondition("the budget is approved", scope -> scope.readState("approved", false))
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(approveBudget, bookFlight)
                .build();

        assertThat(supervisor.invoke("Book a flight to Rome")).isEqualTo("flight booked");

        // the agents offered to the model are those active when it is asked
        List<String> systemMessages = plannerModel.requests().stream()
                .map(request -> request.messages().stream()
                        .filter(SystemMessage.class::isInstance)
                        .map(message -> ((SystemMessage) message).text())
                        .findFirst()
                        .orElseThrow())
                .toList();
        assertThat(systemMessages.get(0)).contains("approveBudget").doesNotContain("bookFlight");
        assertThat(systemMessages.get(1)).contains("approveBudget").contains("bookFlight");
    }

    @Test
    void skip_before_done_does_not_replace_the_last_response() {
        ChatModelMock plannerModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from("""
                {"agentName": "draftTrip", "arguments": {"destination": "Rome"}}
                """),
                AiMessage.from("""
                {"agentName": "bookFlight", "arguments": {"destination": "Rome"}}
                """),
                AiMessage.from("""
                {"agentName": "done", "arguments": {}}
                """));
        AgenticScopeFunction<String> draftTrip = AgenticServices.nonAiAgentBuilder(scope -> "trip to Rome drafted")
                .name("draftTrip")
                .description("Drafts a trip")
                .inputKey(String.class, "destination")
                .outputKey("draft")
                .build();
        AgenticScopeFunction<String> bookFlight = AgenticServices.nonAiAgentBuilder(scope -> "flight booked")
                .name("bookFlight")
                .description("Books a flight")
                .inputKey(String.class, "destination")
                .outputKey("booking")
                .activationCondition("the budget is approved", scope -> scope.readState("approved", false))
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(draftTrip, bookFlight)
                .build();

        // the last response is the one of the last agent actually invoked, not the empty one of the skipped agent
        assertThat(supervisor.invoke("Plan a trip to Rome")).isEqualTo("trip to Rome drafted");
    }
}
