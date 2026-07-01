package dev.langchain4j.agentic.patterns.htn.declarative;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.htn.HtnPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static dev.langchain4j.agentic.patterns.htn.DecompositionMethod.decompose;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.compound;
import static dev.langchain4j.agentic.patterns.htn.TaskNode.primitive;

public class DeclarativeHtnAgents {

    public interface AssessRequirements {
        @UserMessage("""
                Analyze the following trip request. Identify the destination, duration, \
                trip type (adventure vs relaxation), budget, and special requirements. \
                Return a structured summary in 3-5 bullet points.
                Request: {{request}}
                """)
        @Agent(description = "Assess trip requirements from user request", outputKey = "requirements")
        String assess(@V("request") String request);
    }

    public interface PlanTransportation {
        @UserMessage("""
                Plan transportation for the following trip. \
                Suggest flights, car rentals, or other transport options based on the requirements.
                Requirements: {{requirements}}
                """)
        @Agent(description = "Plan trip transportation", outputKey = "transportation")
        String plan(@V("requirements") String requirements);
    }

    public interface FindAccommodation {
        @UserMessage("""
                Find 2-3 suitable accommodations for the trip with pricing and key amenities.
                Requirements: {{requirements}}
                """)
        @Agent(description = "Find suitable accommodation", outputKey = "accommodation")
        String find(@V("requirements") String requirements);
    }

    public interface PlanActivities {
        @UserMessage("""
                Suggest 3-4 outdoor and adventure activities appropriate for the trip destination and duration.
                Requirements: {{requirements}}
                """)
        @Agent(description = "Plan adventure activities", outputKey = "activities")
        String plan(@V("requirements") String requirements);
    }

    public interface PlanDining {
        @UserMessage("""
                Suggest 2-3 dining experiences including restaurant recommendations and cuisine types \
                for a relaxation-focused trip.
                Requirements: {{requirements}}
                """)
        @Agent(description = "Plan dining experiences", outputKey = "dining")
        String plan(@V("requirements") String requirements);
    }

    public interface CreateItinerary {
        @UserMessage("""
                Create a day-by-day trip itinerary summarizing all the planned details.
                Requirements: {{requirements}}
                """)
        @Agent(description = "Create the final trip itinerary", outputKey = "itinerary")
        String create(@V("requirements") String requirements);
    }

    public interface TripPlanner {

        @PlannerAgent(
                outputKey = "itinerary",
                subAgents = {AssessRequirements.class, PlanTransportation.class, FindAccommodation.class,
                        PlanActivities.class, PlanDining.class, CreateItinerary.class})
        String plan(@V("request") String request);

        @PlannerSupplier
        static Planner planner() {
            return new HtnPlanner(
                    compound("Plan Trip",
                            primitive(AssessRequirements.class,
                                    scope -> scope.writeState("tripType", "ADVENTURE")),
                            compound("logistics",
                                    decompose(
                                            scope -> "ADVENTURE".equals(scope.readState("tripType", "")),
                                            primitive(PlanTransportation.class),
                                            primitive(FindAccommodation.class),
                                            primitive(PlanActivities.class)),
                                    decompose(
                                            primitive(FindAccommodation.class),
                                            primitive(PlanDining.class))),
                            primitive(CreateItinerary.class)));
        }

        @ChatModelSupplier
        static ChatModel chatModel() {
            return baseModel();
        }
    }
}
