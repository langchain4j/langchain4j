package dev.langchain4j.agentic.patterns.htn.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.htn.declarative.DeclarativeHtnAgents.TripPlanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class DeclarativeHtnIT {

    @Test
    void declarative_htn_trip_planning() {
        TripPlanner planner = AgenticServices.createAgenticSystem(TripPlanner.class);

        String itinerary = planner.plan(
                "Plan a week-long adventure trip to Patagonia for 2 people. " +
                "We want hiking, camping, and glacier trekking. Budget around $5000.");

        assertThat(itinerary).isNotBlank();
    }
}
