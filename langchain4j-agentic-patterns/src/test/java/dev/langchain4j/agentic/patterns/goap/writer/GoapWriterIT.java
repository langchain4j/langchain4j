package dev.langchain4j.agentic.patterns.goap.writer;

import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.StoryGenerator;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.StyleEditor;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.StyleScorer;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.AudienceEditor;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.StyleReviewLoop;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.Writer;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

public class GoapWriterIT {

    @Test
    void goap_tests() {
        class GoapListener implements AgentListener {
            AtomicBoolean styleEditorCalled = new AtomicBoolean(false);

            @Override
            public void beforeAgentInvocation(AgentRequest request) {
                // Ensure StyleEditor was called before AudienceEditor
                if (request.agentName().equals("audienceEditor")) {
                    assertThat(styleEditorCalled.get()).isTrue();
                }
            }

            @Override
            public void afterAgentInvocation(AgentResponse response) {
                if (response.agentName().equals("styleEditor")) {
                    styleEditorCalled.set(true);
                }
            }
        }

        StoryGenerator storyGenerator = AgenticServices.agentBuilder(StoryGenerator.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .name("audienceEditor")
                .outputKey("finalStory")
                .build();

        StyleEditor styleEditor = AgenticServices.agentBuilder(StyleEditor.class)
                .chatModel(baseModel())
                .name("styleEditor")
                .outputKey("styledStory")
                .build();

        StyleScorer styleScorer = AgenticServices.agentBuilder(StyleScorer.class)
                .chatModel(baseModel())
                .outputKey("score")
                .build();

        StyleReviewLoop styleReviewLoop = AgenticServices.loopBuilder(StyleReviewLoop.class)
                .subAgents(styleEditor, styleScorer)
                .outputKey("styledStory")
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .build();

        Writer writer = AgenticServices.plannerBuilder(Writer.class)
                .subAgents(storyGenerator, audienceEditor, styleReviewLoop)
                .listener(new GoapListener())
                .outputKey("finalStory")
                .planner(GoalOrientedPlanner::new)
                .build();

        ResultWithAgenticScope<String> result = writer.write("dragons and a male wizard", "fantasy", "young adults");
        String story = result.result();
        assertThat(story).contains("dragon").contains("wizard");

        assertThat(result.agenticScope().readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }
}
