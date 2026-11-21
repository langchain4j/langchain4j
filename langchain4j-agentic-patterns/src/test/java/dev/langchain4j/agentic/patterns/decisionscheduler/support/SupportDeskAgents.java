package dev.langchain4j.agentic.patterns.decisionscheduler.support;

import static dev.langchain4j.agentic.patterns.DecisionModels.decisionModel;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A support desk whose specialists gather findings on a customer ticket, and whose reply writer turns them into the
 * reply. Which specialists are worth invoking, and in which order, depends on the ticket.
 */
public class SupportDeskAgents {

    public static final String PARCEL_TICKET = "Where is my order #4711? It should have arrived last week.";
    public static final String FAULTY_MACHINE_TICKET = "My Brewmaster 3000 leaks water from the bottom since the "
            + "first day. How can I fix it, and can I get my money back if it cannot be fixed?";

    public static final String GOAL =
            "The reply answers every question and request of the customer's ticket, using the findings gathered";
    // tuned on jev-1.13.0, where a complete reply was graded from 0.69 to 0.95 and an incomplete one at most 0.55,
    // while a useless specialist scored at most 0.23 and a useful one at least 0.45
    public static final double GOAL_THRESHOLD = 0.6;
    public static final double ACTIVATION_THRESHOLD = 0.3;

    private static final Set<String> SPECIALISTS =
            Set.of("orderTracker", "refundPolicyAdvisor", "troubleshooter", "replyWriter");

    public static DecisionSchedulerPlanner planner() {
        return DecisionSchedulerPlanner.builder()
                .decisionModel(decisionModel())
                .goal(GOAL)
                .goalThreshold(GOAL_THRESHOLD)
                .activationThreshold(ACTIVATION_THRESHOLD)
                .build();
    }

    /** The names of the specialists invoked by the support desk, in the order of their invocations. */
    public static List<String> invokedSpecialists(AgenticScope agenticScope) {
        return agenticScope.agentInvocations().stream()
                .map(AgentInvocation::agentName)
                .filter(SPECIALISTS::contains)
                .toList();
    }

    // ── Findings, which the reply writer reads with a default value when they were not gathered ──

    public static class OrderStatus implements TypedKey<String> {
        @Override
        public String name() {
            return "orderStatus";
        }

        @Override
        public String defaultValue() {
            return "not available";
        }
    }

    public static class RefundPolicy implements TypedKey<String> {
        @Override
        public String name() {
            return "refundPolicy";
        }

        @Override
        public String defaultValue() {
            return "not available";
        }
    }

    public static class Troubleshooting implements TypedKey<String> {
        @Override
        public String name() {
            return "troubleshooting";
        }

        @Override
        public String defaultValue() {
            return "not available";
        }
    }

    // ── Specialists ──

    public static class OrderTracker {

        private static final Pattern ORDER_NUMBER = Pattern.compile("#(\\d+)");

        @Agent(
                name = "orderTracker",
                description = "Looks up the shipping status of the order mentioned in the ticket",
                typedOutputKey = OrderStatus.class)
        public static String track(@V("ticket") String ticket) {
            Matcher orderNumber = ORDER_NUMBER.matcher(ticket);
            if (!orderNumber.find()) {
                return "No order number was found in the ticket.";
            }
            return "4711".equals(orderNumber.group(1))
                    ? "Order #4711: shipped on 2 November, held at customs since 5 November, new expected delivery "
                            + "on 14 November."
                    : "Order #" + orderNumber.group(1) + " is unknown.";
        }
    }

    public interface RefundPolicyAdvisor {

        @UserMessage("""
                You are the refund policy advisor of the store selling the Brewmaster coffee machines.
                The refund policy is: a defective machine can be returned for a full refund within 30 days of
                purchase, with the proof of purchase; after 30 days it is repaired or replaced under the 2-year
                warranty; a machine that works can be returned within 14 days, if unused.
                Explain in no more than 3 sentences what this policy means for the following customer ticket.
                Write only about the refund: say nothing about how to fix the machine, not even that you cannot
                help with it.
                The ticket is: {{ticket}}
                """)
        @Agent(
                name = "refundPolicyAdvisor",
                description = "Explains whether and how the customer can get a refund",
                typedOutputKey = RefundPolicy.class)
        String advise(@V("ticket") String ticket);
    }

    public interface Troubleshooter {

        @UserMessage("""
                You are a technician of the Brewmaster 3000 coffee machine.
                Give in no more than 3 sentences the steps fixing the malfunction described in the following
                customer ticket. Write only the steps: say nothing about refunds, returns or the warranty, not
                even that you cannot help with them.
                The ticket is: {{ticket}}
                """)
        @Agent(
                name = "troubleshooter",
                description = "Finds the steps fixing a malfunction of the Brewmaster 3000 coffee machine",
                typedOutputKey = Troubleshooting.class)
        String troubleshoot(@V("ticket") String ticket);
    }

    public interface ReplyWriter {

        @UserMessage("""
                You are a customer support agent.
                Write a reply of no more than 5 sentences to the following customer ticket, using only the findings
                below and never your own knowledge. Skip the findings that are not available, without mentioning
                them. If no finding answers a part of the ticket, leave that part unanswered.
                The ticket is: {{ticket}}
                Order status: {{orderStatus}}
                Refund policy: {{refundPolicy}}
                Troubleshooting: {{troubleshooting}}
                """)
        @Agent(
                name = "replyWriter",
                description = "Writes the final reply to the customer, once the findings needed to answer every "
                        + "part of the ticket have been gathered",
                outputKey = "reply")
        String write(
                @V("ticket") String ticket,
                @K(OrderStatus.class) String orderStatus,
                @K(RefundPolicy.class) String refundPolicy,
                @K(Troubleshooting.class) String troubleshooting);
    }

    public interface ReplySummarizer {

        @UserMessage("""
                Summarize the following reply to a customer in a single sentence.
                The reply is: {{reply}}
                """)
        @Agent(name = "replySummarizer", description = "Summarizes a reply", outputKey = "summary")
        String summarize(@V("reply") String reply);
    }

    // ── Agentic systems ──

    public interface SupportDesk {

        @Agent
        ResultWithAgenticScope<String> handle(@V("ticket") String ticket);
    }

    public interface SupportPipeline {

        @Agent
        ResultWithAgenticScope<String> process(@V("ticket") String ticket);
    }
}
