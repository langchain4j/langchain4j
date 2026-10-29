package dev.langchain4j.agentic.decision;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.Scale;

/**
 * The decision agents used by the tests of this package, one per scenario of the design record.
 */
public class DecisionAgents {

    public enum RequestCategory {
        @Description("Questions about laws, contracts, liabilities and legal rights")
        LEGAL,
        @Description("Questions about symptoms, diseases, treatments and drugs")
        MEDICAL,
        @Description("Questions about software, hardware, networks and devices")
        TECHNICAL,
        @Description("Requests that belong to none of the other categories")
        UNKNOWN
    }

    /** Scenario 1: a classifier returning the category as an enum. */
    public interface CategoryRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }

    /** Scenario 1: the same classifier, with the probability of each category. */
    public interface ConfidentCategoryRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request, with the probability of each category", outputKey = "category")
        Choice<RequestCategory> classify(@V("request") String request);
    }

    public enum Quality {
        @Description("The story ignores the required style")
        POOR,
        @Description("The story follows the required style only in places")
        FAIR,
        @Description("The story mostly follows the required style")
        GOOD,
        @Description("The story is a perfect example of the required style")
        EXCELLENT
    }

    /** Scenario 2: a scorer returning a position on an ordered scale. */
    public interface StyleScorer {

        @Decide("How well does the story match the required style?")
        @Agent(value = "Scores a story against a style", outputKey = "styleScore")
        Scale<Quality> scoreStyle(@V("story") String story, @V("style") String style);
    }

    /** Scenario 3: a gate answering a yes/no question with a threshold. */
    public interface TransactionGate {

        @Decide("""
                Does this money transfer look suspicious?
                Yes: an unusually large amount, an unknown or foreign beneficiary, an urgent or unusual justification.
                No: a routine payment to a known beneficiary.""")
        @Agent(value = "Checks whether a money transfer looks suspicious", outputKey = "suspicious")
        boolean check(@V("transfer") String transfer);
    }

    /** Scenario 4: an oracle a supervisor can consult, with a typed argument. */
    public interface CreditEligibility {

        @Decide("""
                Is this customer eligible for a credit of the given amount?
                Yes: the amount is at most ten times the monthly income of the customer and the customer has no unpaid debts.
                No: otherwise.""")
        @Agent(
                value =
                        "Answers whether the customer described by the given profile is eligible for a credit of the given amount in USD",
                outputKey = "eligible")
        boolean isEligible(@V("customerProfile") String customerProfile, @V("amount") Double amount);
    }

    public enum Team {
        @Description("Payments, invoices, refunds")
        BILLING,
        @Description("Problems using the product")
        SUPPORT,
        @Description("Questions about plans, prices and purchases")
        SALES
    }

    public enum Sentiment {
        ANGRY,
        NEUTRAL,
        HAPPY
    }

    /** Scenario 5: several questions answered in one call. */
    public record Triage(
            @Decide("Which team should handle this ticket?") Team team,

            @Decide("Does this ticket need attention today?")
            boolean urgent,

            @Decide("What is the sentiment of the customer?")
            Sentiment sentiment) {}

    /** Scenario 5: the method is not annotated with @Decide, the questions are the fields of the record. */
    public interface TicketTriage {

        @Agent(value = "Triages a support ticket", outputKey = "triage")
        Triage triage(@V("ticket") String ticket);
    }

    /** Scenario 5: the same questions, with the probabilities of the team and of the urgency. */
    public record ConfidentTriage(
            @Decide("Which team should handle this ticket?") Choice<Team> team,

            @Decide("Does this ticket need attention today?")
            YesNoAnswer urgent,

            @Decide("What is the sentiment of the customer?")
            Sentiment sentiment) {}

    /** Scenario 5: a record answer holding probabilities, which is not persisted. */
    public interface ConfidentTicketTriage {

        @Agent(value = "Triages a support ticket, with the probability of each team", outputKey = "confidentTriage")
        ConfidentTriage triage(@V("ticket") String ticket);
    }

    /** Scenario 7: a decision on an image. */
    public interface BannerCheck {

        @Decide("Does this image contain any written text?")
        @Agent(value = "Checks whether a banner image contains text", outputKey = "containsText")
        boolean containsText(@V("banner") ImageContent banner);
    }

    /** A yes/no question answered with its probability. */
    public interface SpamCheck {

        @Decide("Is this message spam?")
        @Agent(value = "Checks whether a message is spam", outputKey = "spam")
        YesNoAnswer isSpam(@V("message") String message);
    }

    /** The category together with the raw response of the model. */
    public interface AuditedCategoryRouter {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        DecisionResult<RequestCategory> classify(@V("request") String request);
    }

    /** A decision agent registering its own monitor. */
    public interface MonitoredCategoryRouter extends MonitoredAgent {

        @Decide("Which kind of expert should handle this request?")
        @Agent(value = "Categorizes a user request", outputKey = "category")
        RequestCategory classify(@V("request") String request);
    }
}
