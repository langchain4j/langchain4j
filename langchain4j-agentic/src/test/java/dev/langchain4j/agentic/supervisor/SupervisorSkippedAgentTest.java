package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupervisorSkippedAgentTest {

    /** Replies with the given responses in order, capturing the last user message of each request. */
    static class ScriptedChatModel implements ChatModel {

        private final Iterator<String> responses;
        final List<String> userMessages = new ArrayList<>();

        ScriptedChatModel(String... responses) {
            this.responses = List.of(responses).iterator();
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            userMessages.add(((UserMessage)
                            chatRequest.messages().get(chatRequest.messages().size() - 1))
                    .singleText());
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responses.next()))
                    .build();
        }
    }

    @Test
    void supervisor_is_told_why_an_agent_was_skipped() {
        ScriptedChatModel plannerModel = new ScriptedChatModel("""
                {"agentName": "bookFlight", "arguments": {"destination": "Rome"}}
                """, """
                {"agentName": "done", "arguments": {"response": "no flight booked"}}
                """);
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
        assertThat(plannerModel.userMessages)
                .hasSize(2)
                .last()
                .asString()
                .contains("The last received response is: 'agent 'bookFlight' was skipped: "
                        + "activation condition is false: the budget is approved'");
    }
}
