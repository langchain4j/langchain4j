package dev.langchain4j.agentic.patterns.htn.event;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public class EventAgents {

    public interface AnalyzeRequest {
        @UserMessage("""
                Analyze the following event request. Extract the event type, expected number of attendees, \
                location preferences, and any special requirements. Return a structured summary in 3-5 bullet points.
                Request: {{request}}
                """)
        @Agent("Analyze an event planning request to extract requirements")
        String analyze(@V("request") String request);
    }

    public interface FindVenues {
        @UserMessage("""
                Based on the following event analysis, suggest 2-3 suitable venues. \
                Include capacity, location, and key amenities for each. Return only the venue suggestions.
                Event analysis: {{eventAnalysis}}
                """)
        @Agent("Find suitable venues for the event")
        String findVenues(@V("eventAnalysis") String eventAnalysis);
    }

    public interface PlanCatering {
        @UserMessage("""
                Plan catering for the following event. Suggest a menu appropriate for the event type and attendee count. \
                Include dietary considerations. Return only the catering plan.
                Event analysis: {{eventAnalysis}}
                """)
        @Agent("Plan catering for the event")
        String planCatering(@V("eventAnalysis") String eventAnalysis);
    }

    public interface ArrangeTransportation {
        @UserMessage("""
                Arrange transportation logistics for the following event. \
                Consider the venue location and number of attendees. Return only the transportation plan.
                Event analysis: {{eventAnalysis}}
                Venues: {{venues}}
                """)
        @Agent("Arrange transportation logistics for the event")
        String arrangeTransportation(@V("eventAnalysis") String eventAnalysis, @V("venues") String venues);
    }

    public interface DesignActivities {
        @UserMessage("""
                Design team-building or entertainment activities for the following event. \
                Suggest 3-4 activities appropriate for the event type and attendees. Return only the activity descriptions.
                Event analysis: {{eventAnalysis}}
                """)
        @Agent("Design activities and entertainment for the event")
        String designActivities(@V("eventAnalysis") String eventAnalysis);
    }

    public interface CreateBudget {
        @UserMessage("""
                Create a budget estimate for the event based on all the gathered information. \
                Break down costs by category. Return only the budget breakdown.
                Event analysis: {{eventAnalysis}}
                Venues: {{venues}}
                Catering: {{catering}}
                Activities: {{activities}}
                """)
        @Agent("Create a budget estimate for the event")
        String createBudget(@V("eventAnalysis") String eventAnalysis,
                            @V("venues") String venues,
                            @V("catering") String catering,
                            @V("activities") String activities);
    }

    public interface CompilePlan {
        @UserMessage("""
                Compile a final event plan from all gathered information into a professional document. \
                Include all sections: venue, catering, activities, transportation (if available), and budget.
                Event analysis: {{eventAnalysis}}
                Venues: {{venues}}
                Budget: {{budget}}
                """)
        @Agent("Compile the final event plan")
        String compilePlan(@V("eventAnalysis") String eventAnalysis,
                           @V("venues") String venues,
                           @V("budget") String budget);
    }

    public interface EventPlanner extends MonitoredAgent {
        @Agent
        ResultWithAgenticScope<String> plan(@V("request") String request);
    }
}
