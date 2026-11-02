package dev.langchain4j.agentic.decision;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.decision.DecisionModels.typeSafe;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.decision.DecisionAgents.Sentiment;
import dev.langchain4j.agentic.decision.DecisionAgents.Team;
import dev.langchain4j.agentic.decision.DecisionAgents.TicketTriage;
import dev.langchain4j.agentic.decision.DecisionAgents.Triage;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Scenario 5: one decision call answers several questions about a ticket; its fields are spread into the scope and
 * feed a reply agent and an escalation agent.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionTriageFanOutIT {

    private static final String ANGRY_TICKET =
            "I was charged twice for my subscription this month and nobody answers my emails. Fix this today or I cancel!";
    private static final String HAPPY_TICKET =
            "Thanks for the quick delivery, the product is great! Do you plan to sell it in a larger size?";

    public interface ReplyAgent {

        @UserMessage("""
                You work in the {{team}} team of an online shop. Write a two-sentence reply to the following ticket,
                knowing that the mood of the customer is {{sentiment}}.
                The ticket is: "{{ticket}}"
                """)
        @Agent(value = "Replies to a support ticket", outputKey = "reply")
        String reply(@V("team") Team team, @V("sentiment") Sentiment sentiment, @V("ticket") String ticket);
    }

    private static UntypedAgent ticketSystem() {
        TicketTriage triage = AgenticServices.decisionAgentBuilder(TicketTriage.class)
                .decisionModel(typeSafe())
                .build();
        ReplyAgent replyAgent = AgenticServices.agentBuilder(ReplyAgent.class)
                .chatModel(baseModel())
                .build();
        Object escalator = AgenticServices.nonAiAgentBuilder(
                        scope -> "escalated to the " + scope.readState("team") + " team lead")
                .name("escalator")
                .description("Escalates an urgent ticket")
                .inputKey(Team.class, "team")
                .outputKey("escalation")
                .build();
        UntypedAgent handlers = AgenticServices.conditionalBuilder()
                .beforeCall(DecisionTriageFanOutIT::spreadTriage)
                .subAgents("every ticket gets a reply", scope -> true, replyAgent)
                .subAgents("the ticket is urgent", scope -> scope.readState("urgent", false), escalator)
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(triage, handlers)
                .outputKey("reply")
                .build();
    }

    private static void spreadTriage(AgenticScope scope) {
        Triage triage = (Triage) scope.readState("triage");
        scope.writeState("team", triage.team());
        scope.writeState("urgent", triage.urgent());
        scope.writeState("sentiment", triage.sentiment());
    }

    @Test
    void escalates_an_urgent_billing_ticket_and_replies_to_it() {
        ResultWithAgenticScope<String> result = ticketSystem().invokeWithAgenticScope(Map.of("ticket", ANGRY_TICKET));

        Triage triage = (Triage) result.agenticScope().readState("triage");
        System.out.println(triage + "\n" + result.result());
        assertThat(triage.team()).isEqualTo(Team.BILLING);
        assertThat(triage.urgent()).isTrue();
        assertThat(triage.sentiment()).isEqualTo(Sentiment.ANGRY);
        assertThat(result.result()).isNotBlank();
        assertThat(result.agenticScope().readState("escalation")).isEqualTo("escalated to the BILLING team lead");
    }

    @Test
    void replies_to_a_happy_ticket_without_escalating_it() {
        ResultWithAgenticScope<String> result = ticketSystem().invokeWithAgenticScope(Map.of("ticket", HAPPY_TICKET));

        Triage triage = (Triage) result.agenticScope().readState("triage");
        System.out.println(triage + "\n" + result.result());
        assertThat(triage.urgent()).isFalse();
        assertThat(triage.sentiment()).isEqualTo(Sentiment.HAPPY);
        assertThat(result.result()).isNotBlank();
        assertThat(result.agenticScope().hasState("escalation")).isFalse();
    }
}
