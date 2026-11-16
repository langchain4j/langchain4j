package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.test.mock.MockChatModel;
import org.junit.jupiter.api.Test;

class SupervisorEnumArgumentTest {

    public enum Category {
        LEGAL,
        MEDICAL
    }

    public static class RouterAgent {

        @Agent(description = "Routes a request to the expert of its category", outputKey = "routed")
        public String route(@V("category") Category category) {
            return "routed to " + category;
        }
    }

    @Test
    void supervisor_passes_an_enum_argument_by_the_name_of_its_constant() {
        MockChatModel plannerModel = MockChatModel.thatResponds(AiMessage.from("""
                {"agentName": "route", "arguments": {"category": "LEGAL"}}
                """), AiMessage.from("""
                {"agentName": "done", "arguments": {"response": "routed"}}
                """));

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(new RouterAgent())
                .build();

        assertThat(supervisor.invoke("I need a lawyer")).isEqualTo("routed to LEGAL");
    }
}
