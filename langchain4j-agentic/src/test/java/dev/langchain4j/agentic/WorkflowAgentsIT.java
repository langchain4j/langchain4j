package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.cognisphere.CognispherePersister;
import dev.langchain4j.agentic.cognisphere.CognisphereRegistry;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.StyleEditor;
import dev.langchain4j.agentic.Agents.StyleScorer;
import dev.langchain4j.agentic.Agents.StyledWriter;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.TechnicalExpert;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpertWithMemory;
import dev.langchain4j.agentic.Agents.TechnicalExpertWithMemory;
import dev.langchain4j.agentic.Agents.LegalExpertWithMemory;
import dev.langchain4j.agentic.Agents.RequestCategory;
import dev.langchain4j.agentic.Agents.ExpertRouterAgent;
import dev.langchain4j.agentic.Agents.ExpertRouterAgentWithMemory;
import dev.langchain4j.agentic.Agents.MovieExpert;
import dev.langchain4j.agentic.Agents.FoodExpert;
import dev.langchain4j.agentic.Agents.EveningPlannerAgent;
import dev.langchain4j.agentic.Agents.EveningPlan;

import static dev.langchain4j.agentic.Models.BASE_MODEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WorkflowAgentsIT {

    @Test
    void sequential_agents_tests() {
        CreativeWriter creativeWriter = spy(AgentServices.agentBuilder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        AudienceEditor audienceEditor = spy(AgentServices.agentBuilder(AudienceEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        StyleEditor styleEditor = spy(AgentServices.agentBuilder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        UntypedAgent novelCreator = AgentServices.sequenceBuilder()
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
    void sequential_agents_with_human_in_the_loop_tests() {
        CreativeWriter creativeWriter = spy(AgentServices.agentBuilder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        AtomicReference<String> request = new AtomicReference<>();

        HumanInTheLoop humanInTheLoop = AgentServices.humanInTheLoopBuilder()
                .description("An agent that asks the audience for the story")
                .inputName("topic")
                .outputName("audience")
                .requestWriter(q -> request.set("Which audience for topic " + q + "?"))
                .responseReader(() -> "young adults")
                .build();

        AudienceEditor audienceEditor = spy(AgentServices.agentBuilder(AudienceEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build());

        UntypedAgent novelCreator = AgentServices.sequenceBuilder()
                .subAgents(creativeWriter, humanInTheLoop, audienceEditor)
                .outputName("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards"
        );

        String story = (String) novelCreator.invoke(input);
        System.out.println(story);

        assertThat(request.get()).isEqualTo("Which audience for topic dragons and wizards?");

        verify(creativeWriter).generateStory("dragons and wizards");
        verify(audienceEditor).editStory(any(), eq("young adults"));
    }

    @Test
    void loop_agents_tests() {
        CreativeWriter creativeWriter = AgentServices.agentBuilder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.agentBuilder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.agentBuilder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        UntypedAgent styleReviewLoop = AgentServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        UntypedAgent styledWriter = AgentServices.sequenceBuilder()
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "comedy"
        );

        ResultWithCognisphere<String> result = styledWriter.invokeWithCognisphere(input);
        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void typed_loop_agents_tests() {
        CreativeWriter creativeWriter = AgentServices.agentBuilder(CreativeWriter.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.agentBuilder(StyleEditor.class)
                .chatModel(BASE_MODEL)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.agentBuilder(StyleScorer.class)
                .chatModel(BASE_MODEL)
                .outputName("score")
                .build();

        UntypedAgent styleReviewLoop = AgentServices.loopBuilder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = AgentServices.sequenceBuilder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        ResultWithCognisphere<String> result = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        String story = result.result();
        System.out.println(story);

        Cognisphere cognisphere = result.cognisphere();
        // Verify that an ephemeral cognisphere is correctly evicted from the registry after the call
        assertThat(styledWriter.getCognisphere(cognisphere.id())).isNull();

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
        CategoryRouter routerAgent = AgentServices.agentBuilder(CategoryRouter.class)
                .chatModel(BASE_MODEL)
                .outputName("category")
                .build();

        MedicalExpert medicalExpert = spy(AgentServices.agentBuilder(MedicalExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build());
        LegalExpert legalExpert = spy(AgentServices.agentBuilder(LegalExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build());
        TechnicalExpert technicalExpert = spy(AgentServices.agentBuilder(TechnicalExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("response")
                .build());

        UntypedAgent expertsAgent = AgentServices.conditionalBuilder()
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
                .build();

        ExpertRouterAgent expertRouterAgent = AgentServices.sequenceBuilder(ExpertRouterAgent.class)
                .subAgents(routerAgent, expertsAgent)
                .outputName("response")
                .build();

        System.out.println(expertRouterAgent.ask("I broke my leg what should I do"));

        verify(medicalExpert).medical("I broke my leg what should I do");
    }

    @Test
    void memory_agents_tests() {
        CategoryRouter routerAgent = spy(AgentServices.agentBuilder(CategoryRouter.class)
                .chatModel(BASE_MODEL)
                .outputName("category")
                .build());

        MedicalExpertWithMemory medicalExpert = spy(AgentServices.agentBuilder(MedicalExpertWithMemory.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("response")
                .build());
        TechnicalExpertWithMemory technicalExpert = spy(AgentServices.agentBuilder(TechnicalExpertWithMemory.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .outputName("response")
                .build());
        LegalExpertWithMemory legalExpert = spy(AgentServices.agentBuilder(LegalExpertWithMemory.class)
                .chatModel(BASE_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .summarizedContext("medical", "technical")
                .outputName("response")
                .build());

        UntypedAgent expertsAgent = AgentServices.conditionalBuilder()
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.MEDICAL, medicalExpert)
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.TECHNICAL, technicalExpert)
                .subAgents( cognisphere -> cognisphere.readState("category", RequestCategory.UNKNOWN) == RequestCategory.LEGAL, legalExpert)
                .build();

        ExpertRouterAgentWithMemory expertRouterAgent = AgentServices.sequenceBuilder(ExpertRouterAgentWithMemory.class)
                .subAgents(routerAgent, expertsAgent)
                .outputName("response")
                .build();

        JsonInMemoryCognispherePersistenceProvider provider = new JsonInMemoryCognispherePersistenceProvider();
        CognispherePersister.setPersistenceProvider(provider);

        String response1 = expertRouterAgent.ask("1", "I broke my leg, what should I do?");
        System.out.println(response1);

        Cognisphere cognisphere1 = expertRouterAgent.getCognisphere("1");
        assertThat(cognisphere1.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.MEDICAL);

        assertThat(provider.getAllIds()).isEqualTo(CognisphereRegistry.getAllCognisphereKeysInMemory());
        assertThat(provider.getLoadedIds()).isEmpty();

        String response2 = expertRouterAgent.ask("2", "My computer has liquid inside, what should I do?");
        System.out.println(response2);

        Cognisphere cognisphere2 = expertRouterAgent.getCognisphere("2");
        assertThat(cognisphere2.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.TECHNICAL);

        assertThat(provider.getAllIds()).isEqualTo(CognisphereRegistry.getAllCognisphereKeysInMemory());

        // Clear the in-memory registry to simulate a restart
        CognisphereRegistry.clearInMemory();
        assertThat(CognisphereRegistry.getAllCognisphereKeysInMemory()).isEmpty();

        String legalResponse1 = expertRouterAgent.ask("1", "Should I sue my neighbor who caused this damage?");
        System.out.println(legalResponse1);

        String legalResponse2 = expertRouterAgent.ask("2", "Should I sue my neighbor who caused this damage?");
        System.out.println(legalResponse2);

        assertThat(provider.getLoadedIds()).isEqualTo(List.of("1", "2"));

        assertThat(legalResponse1).contains("medical").doesNotContain("computer");
        assertThat(legalResponse2).contains("computer").doesNotContain("medical");

        verify(routerAgent).classify("I broke my leg, what should I do?");
        verify(routerAgent).classify("My computer has liquid inside, what should I do?");
        verify(routerAgent, times(2)).classify("Should I sue my neighbor who caused this damage?");

        verify(medicalExpert).medical("1", "I broke my leg, what should I do?");

        verify(technicalExpert).technical("2", "My computer has liquid inside, what should I do?");

        verify(legalExpert).legal(eq("1"), any());
        verify(legalExpert).legal(eq("2"), any());

        // It is necessary to read again the cognisphere instances since they were evicted from the in-memory registry
        // and reloaded from the persistence provider
        cognisphere1 = expertRouterAgent.getCognisphere("1");
        assertThat(cognisphere1.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);
        cognisphere2 = expertRouterAgent.getCognisphere("2");
        assertThat(cognisphere2.readState("category", RequestCategory.UNKNOWN)).isEqualTo(RequestCategory.LEGAL);

        assertThat(expertRouterAgent.evictCognisphere("1")).isTrue();
        assertThat(expertRouterAgent.evictCognisphere("2")).isTrue();
        assertThat(expertRouterAgent.evictCognisphere("1")).isFalse();
        assertThat(expertRouterAgent.evictCognisphere("2")).isFalse();
    }

    @Test
    void parallel_agents_tests() {
        FoodExpert foodExpert = AgentServices.agentBuilder(FoodExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("meals")
                .build();

        MovieExpert movieExpert = AgentServices.agentBuilder(MovieExpert.class)
                .chatModel(BASE_MODEL)
                .outputName("movies")
                .build();

        EveningPlannerAgent eveningPlannerAgent = AgentServices.parallelBuilder(EveningPlannerAgent.class)
                .subAgents(foodExpert, movieExpert)
                .outputName("plans")
                .executorService(Executors.newFixedThreadPool(2))
                .output(cognisphere -> {
                    List<String> movies = cognisphere.readState("movies", List.of());
                    List<String> meals = cognisphere.readState("meals", List.of());

                    List<EveningPlan> moviesAndMeals = new ArrayList<>();
                    for (int i = 0; i < movies.size(); i++) {
                        if (i >= meals.size()) {
                            break;
                        }
                        moviesAndMeals.add(new EveningPlan(movies.get(i), meals.get(i)));
                    }
                    return moviesAndMeals;
                })
                .build();

        List<EveningPlan> plans = eveningPlannerAgent.plan("romantic");
        System.out.println(plans);
        assertThat(plans).hasSize(3);
    }
}
