package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.data.message.AiMessage;
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
        AgenticScopeFunction<String> bookFlight = AgenticServices.nonAiAgentBuilder(scope -> {
                    invocations.incrementAndGet();
                    return "stale booking";
                })
                .name("bookFlight")
                .description("Books a flight")
                .inputKey(String.class, "destination")
                .outputKey("booking")
                .activationCondition("the budget is approved", scope -> scope.readState("approved", false))
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
}
