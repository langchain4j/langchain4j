package dev.langchain4j.agentic;

import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.workflow.ConditionialAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.StyledWriter;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.RequestCategory;
import dev.langchain4j.agentic.Agents.ExpertRouterAgent;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class WorkflowAgentsIT {

    @Test
    void sequential_agents_tests() {
        CreativeWriter creativeWriter = spy(AgentServices.builder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        AudienceEditor audienceEditor = spy(AgentServices.builder(AudienceEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        StyleEditor styleEditor = spy(AgentServices.builder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        UntypedAgent novelCreator = SequentialAgentService.builder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputName("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults"
        );

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);

        verify(creativeWriter).generateStory("dragons and wizards");
        verify(audienceEditor).editStory(any(), eq("young adults"));
        verify(styleEditor).editStory(any(), eq("fantasy"));
    }

    @Test
    void loop_agents_tests() {
        CreativeWriter creativeWriter = AgentServices.builder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.builder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.builder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        Object styleReviewLoop = LoopAgentService.builder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        UntypedAgent styledWriter = SequentialAgentService.builder()
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "comedy"
        );

        String story = (String) styledWriter.invoke(input);
        System.out.println(story);

        Cognisphere cognisphere = ((CognisphereOwner) styledWriter).cognisphere();
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void typed_loop_agents_tests() {
        CreativeWriter creativeWriter = AgentServices.builder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.builder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.builder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        UntypedAgent styleReviewLoop = LoopAgentService.builder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = SequentialAgentService.builder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        String story = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        System.out.println(story);

        Cognisphere cognisphere = ((CognisphereOwner) styledWriter).cognisphere();
        assertThat(cognisphere.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(cognisphere.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(cognisphere.getAgentInvocations("generateStory")).hasSize(1);

        List<AgentCall> scoreAgentCalls = cognisphere.getAgentInvocations("scoreStyle");
        assertThat(scoreAgentCalls).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentCalls);
        assertThat((Double) scoreAgentCalls.get(scoreAgentCalls.size() - 1).response()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void conditional_agents_tests() {
        CategoryRouter routerAgent = AgentServices.builder(CategoryRouter.class)
                .chatModel(BASE_MODEL)
                .outputName("category")
                .build();

        MedicalExpert medicalExpert = spy(AgentServices.builder(MedicalExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build());
        LegalExpert legalExpert = spy(AgentServices.builder(LegalExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build());
        TechnicalExpert technicalExpert = spy(AgentServices.builder(TechnicalExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build());

        UntypedAgent expertsAgent = ConditionialAgentService.builder()
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
                .build();

        ExpertRouterAgent expertRouterAgent = SequentialAgentService.builder(ExpertRouterAgent.class)
                .subAgents(routerAgent, expertsAgent)
                .outputName("response")
                .build();

        System.out.println(expertRouterAgent.ask("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

}
