package dev.langchain4j.agentic.patterns.goap.horoscope;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;

import java.util.Map;

import static dev.langchain4j.agentic.patterns.Models.baseModel;

public class GoapBasedHoroscopeWriter {

    public static void main(String[] args) {
        HoroscopeGenerator horoscopeGenerator = AgenticServices.agentBuilder(HoroscopeGenerator.class)
                .chatModel(baseModel())
                .outputKey("horoscope")
                .build();

        PersonExtractor personExtractor = AgenticServices.agentBuilder(PersonExtractor.class)
                .chatModel(baseModel())
                .outputKey("person")
                .build();

        SignExtractor signExtractor = AgenticServices.agentBuilder(SignExtractor.class)
                .chatModel(baseModel())
                .outputKey("sign")
                .build();

        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(baseModel())
                .outputKey("writeup")
                .build();

        StoryFinder storyFinder = AgenticServices.agentBuilder(StoryFinder.class)
                .chatModel(baseModel())
                .tools(new WebSearchTool())
                .outputKey("story")
                .build();

        UntypedAgent horoscopeAgent = AgenticServices.plannerBuilder()
                .subAgents(horoscopeGenerator, personExtractor, signExtractor, writer, storyFinder)
                .outputKey("writeup")
                .planner(GoalOrientedPlanner::new)
                .build();

        Map<String, Object> input = Map.of("prompt", "My name is Mario and my zodiac sign is pisces");
        System.out.println(horoscopeAgent.invoke(input));
    }
}
