package dev.langchain4j.agentic.patterns.decisionscheduler.support;

import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.FAULTY_MACHINE_TICKET;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.PARCEL_TICKET;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.invokedSpecialists;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.DeclarativeSupportDeskAgents.DeclarativeSupportDesk;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DeclarativeDecisionSchedulerSupportDeskIT {

    @Test
    void declarative_support_desk_sends_a_parcel_ticket_to_the_order_tracker_and_then_to_the_reply_writer() {
        DeclarativeSupportDesk supportDesk = AgenticServices.createAgenticSystem(DeclarativeSupportDesk.class);

        ResultWithAgenticScope<String> result = supportDesk.handle(PARCEL_TICKET);

        List<String> path = invokedSpecialists(result.agenticScope());
        System.out.println("Declarative parcel ticket path: " + path);

        assertThat(path).startsWith("orderTracker").endsWith("replyWriter").doesNotContain("troubleshooter");
        assertThat(result.result()).containsIgnoringCase("customs");
    }

    @Test
    void declarative_support_desk_never_sends_a_faulty_machine_ticket_to_the_order_tracker() {
        DeclarativeSupportDesk supportDesk = AgenticServices.createAgenticSystem(DeclarativeSupportDesk.class);

        ResultWithAgenticScope<String> result = supportDesk.handle(FAULTY_MACHINE_TICKET);

        List<String> path = invokedSpecialists(result.agenticScope());
        System.out.println("Declarative faulty machine ticket path: " + path);

        assertThat(path).endsWith("replyWriter").doesNotContain("orderTracker");
        assertThat(path.subList(0, path.lastIndexOf("replyWriter")))
                .contains("troubleshooter", "refundPolicyAdvisor");
        assertThat(result.result()).isNotBlank();
    }
}
