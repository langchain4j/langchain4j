package dev.langchain4j.agentic.patterns.decisionscheduler.story;

import static dev.langchain4j.agentic.patterns.DecisionModels.decisionModel;
import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.patterns.decisionscheduler.DecisionSchedulerPlanner;
import dev.langchain4j.agentic.patterns.decisionscheduler.story.StoryAgents.AudienceEditor;
import dev.langchain4j.agentic.patterns.decisionscheduler.story.StoryAgents.StoryGenerator;
import dev.langchain4j.agentic.patterns.decisionscheduler.story.StoryAgents.StoryWriter;
import dev.langchain4j.agentic.patterns.decisionscheduler.story.StoryAgents.StyleEditor;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The editors rewrite the story that they also read, so each of them can run again after every rewrite: the decision
 * scheduler alternates them until the decision model considers the goal reached, within the maximum number of
 * invocations.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
public class DecisionSchedulerStoryIT {

    private static final int MAX_INVOCATIONS = 6;
    private static final Set<String> AGENTS = Set.of("storyGenerator", "styleEditor", "audienceEditor");

    @Test
    void story_is_generated_once_and_then_edited_until_it_fits_the_style_and_the_audience() {
        StoryGenerator storyGenerator = AgenticServices.agentBuilder(StoryGenerator.class)
                .chatModel(baseModel())
                .build();
        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .build();
        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .build();

        StoryWriter storyWriter = AgenticServices.plannerBuilder(StoryWriter.class)
                .subAgents(storyGenerator, styleEditor, audienceEditor)
                .outputKey("story")
                .planner(() -> DecisionSchedulerPlanner.builder()
                        .decisionModel(decisionModel())
                        .goal(StoryAgents.GOAL)
                        .goalThreshold(0.5)
                        .activationThreshold(0.5)
                        .maxInvocations(MAX_INVOCATIONS)
                        .build())
                .build();

        ResultWithAgenticScope<String> result =
                storyWriter.write("a dragon who is afraid of fire", "film noir", "children aged six");

        List<String> path = result.agenticScope().agentInvocations().stream()
                .map(AgentInvocation::agentName)
                .filter(AGENTS::contains)
                .toList();
        System.out.println("Story path: " + path);
        System.out.println("Story: " + result.result());

        assertThat(result.result()).isNotBlank();
        // the topic never changes, so the generator runs only once
        assertThat(path).startsWith("storyGenerator").containsOnlyOnce("storyGenerator");
        assertThat(path).containsAnyOf("styleEditor", "audienceEditor");
        assertThat(path).hasSizeLessThanOrEqualTo(MAX_INVOCATIONS);
    }
}
