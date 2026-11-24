package dev.langchain4j.agentic.patterns.decisionscheduler.support;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.FAULTY_MACHINE_TICKET;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.PARCEL_TICKET;
import static dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.invokedSpecialists;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.OrderTracker;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.RefundPolicyAdvisor;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.ReplyWriter;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.SupportDesk;
import dev.langchain4j.agentic.patterns.decisionscheduler.support.SupportDeskAgents.Troubleshooter;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The decision scheduler invokes only the specialists that are useful for the ticket, in an order that depends on the
 * ticket and on the findings gathered so far.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionSchedulerSupportDeskIT {

    static SupportDesk supportDesk() {
        RefundPolicyAdvisor refundPolicyAdvisor = AgenticServices.agentBuilder(RefundPolicyAdvisor.class)
                .chatModel(baseModel())
                .build();
        Troubleshooter troubleshooter = AgenticServices.agentBuilder(Troubleshooter.class)
                .chatModel(baseModel())
                .build();
        ReplyWriter replyWriter = AgenticServices.agentBuilder(ReplyWriter.class)
                .chatModel(baseModel())
                .build();

        return AgenticServices.plannerBuilder(SupportDesk.class)
                .subAgents(OrderTracker.class, refundPolicyAdvisor, troubleshooter, replyWriter)
                .outputKey("reply")
                .planner(SupportDeskAgents::planner)
                .build();
    }

    @Test
    void parcel_ticket_goes_to_the_order_tracker_and_then_to_the_reply_writer() {
        ResultWithAgenticScope<String> result = supportDesk().handle(PARCEL_TICKET);

        List<String> path = invokedSpecialists(result.agenticScope());
        System.out.println("Parcel ticket path: " + path);
        System.out.println("Parcel ticket reply: " + result.result());

        assertThat(path).startsWith("orderTracker").endsWith("replyWriter").doesNotContain("troubleshooter");
        // the reason of the delay is only known to the order tracker
        assertThat(result.result()).containsIgnoringCase("customs");
    }

    @Test
    void faulty_machine_ticket_goes_to_the_troubleshooter_and_to_the_refund_advisor_before_the_reply_writer() {
        ResultWithAgenticScope<String> result = supportDesk().handle(FAULTY_MACHINE_TICKET);

        List<String> path = invokedSpecialists(result.agenticScope());
        System.out.println("Faulty machine ticket path: " + path);
        System.out.println("Faulty machine ticket reply: " + result.result());

        assertThat(path).endsWith("replyWriter").doesNotContain("orderTracker");
        assertThat(path.subList(0, path.lastIndexOf("replyWriter")))
                .contains("troubleshooter", "refundPolicyAdvisor");
        assertThat(result.result()).isNotBlank();
    }
}
