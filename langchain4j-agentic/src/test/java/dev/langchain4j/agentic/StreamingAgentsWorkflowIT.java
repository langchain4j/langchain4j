package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.plannerModel;
import static dev.langchain4j.agentic.Models.streamingBaseModel;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.AudienceEditorForStreaming;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.ExpertRouterAgentForStreaming;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.LegalExpertForStreaming;
import dev.langchain4j.agentic.Agents.MedicalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpertForStreaming;
import dev.langchain4j.agentic.Agents.NovelCreatorForStreaming;
import dev.langchain4j.agentic.Agents.StyleEditorForStreaming;
import dev.langchain4j.agentic.Agents.TechnicalExpertForStreaming;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgentService;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.ConditionalAgentService;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

public class StreamingAgentsWorkflowIT {

    @Test
    void streaming_agent_only_in_last_sequence_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        TokenStream tokenStream = (TokenStream) novelCreator.invoke(input);
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        tokenStream
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String story = futureAnswer.get(60, SECONDS);
        ChatResponse response = futureResponse.get(60, SECONDS);

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void streaming_agent_not_in_last_sequence_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditorForStreaming audienceEditor = AgenticServices.agentBuilder(AudienceEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        final SequentialAgentService<UntypedAgent> sequentialAgentService = AgenticServices.sequenceBuilder();
        sequentialAgentService
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sequentialAgentService.build())
                .withMessage("Only the last sub-agent can return TokenStream.");
    }

    @Test
    void streaming_agent_not_same_output_sequence_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        final SequentialAgentService<UntypedAgent> sequentialAgentService = AgenticServices.sequenceBuilder();
        sequentialAgentService
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story1");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sequentialAgentService.build())
                .withMessage("The last sub-agent and the workflow should have the same outputKey.");
    }

    @Test
    void streaming_sequence_agent_in_sequence_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        UntypedAgent novelCreator1 = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        UntypedAgent novelCreator = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, novelCreator1)
                .outputKey("story")
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults");

        TokenStream tokenStream = (TokenStream) novelCreator.invoke(input);
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        tokenStream
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String story = futureAnswer.get(60, SECONDS);
        ChatResponse response = futureResponse.get(60, SECONDS);

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void streaming_sequence_agent_in_sequence_workflow_2() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        UntypedAgent novelCreator1 = AgenticServices.sequenceBuilder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        final SequentialAgentService<UntypedAgent> sequentialAgentService = AgenticServices.sequenceBuilder()
                .subAgents(novelCreator1, creativeWriter, audienceEditor)
                .outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sequentialAgentService.build())
                .withMessage("Only the last sub-agent can return TokenStream.");
    }

    @Test
    void streaming_sequence_agent_in_sequence_workflow_2_typed() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        NovelCreatorForStreaming novelCreator1 = AgenticServices.sequenceBuilder(NovelCreatorForStreaming.class)
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        final SequentialAgentService<UntypedAgent> sequentialAgentService = AgenticServices.sequenceBuilder()
                .subAgents(novelCreator1, creativeWriter, audienceEditor)
                .outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sequentialAgentService.build())
                .withMessage("Only the last sub-agent can return TokenStream.");
    }

    @Test
    void streaming_agent_in_parallel_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        final ParallelAgentService<UntypedAgent> parallelAgentService = AgenticServices.parallelBuilder();
        parallelAgentService
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> parallelAgentService.build())
                .withMessage("Agent cannot be used as a sub-agent because it returns TokenStream.");
    }

    @Test
    void streaming_agent_in_loop_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        final LoopAgentService<UntypedAgent> loopAgentService = AgenticServices.loopBuilder();
        loopAgentService
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .maxIterations(5)
                .exitCondition(agenticScope -> agenticScope.readState("score", 0.0) >= 0.8)
                .outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loopAgentService.build())
                .withMessage("Agent cannot be used as a sub-agent because it returns TokenStream.");
    }

    @Test
    void all_streaming_agent_in_conditional_workflow_untyped() throws Exception {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpertForStreaming medicalExpert = AgenticServices.agentBuilder(MedicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        LegalExpertForStreaming legalExpert = AgenticServices.agentBuilder(LegalExpertForStreaming.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();
        TechnicalExpertForStreaming technicalExpert = AgenticServices.agentBuilder(TechnicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .outputKey("response")
                .build();

        final UntypedAgent untypedAgent = AgenticServices.sequenceBuilder()
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        Map<String, Object> input = Map.of("request", "I broke my leg what should I do");

        TokenStream tokenStream = (TokenStream) untypedAgent.invoke(input);
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        tokenStream
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String story = futureAnswer.get(60, SECONDS);
        ChatResponse response = futureResponse.get(60, SECONDS);

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void all_streaming_agent_in_conditional_workflow_typed() throws Exception {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpertForStreaming medicalExpert = AgenticServices.agentBuilder(MedicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        LegalExpertForStreaming legalExpert = AgenticServices.agentBuilder(LegalExpertForStreaming.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();
        TechnicalExpertForStreaming technicalExpert = AgenticServices.agentBuilder(TechnicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .outputKey("response")
                .build();

        ExpertRouterAgentForStreaming expertRouterAgent = AgenticServices.sequenceBuilder(
                        ExpertRouterAgentForStreaming.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        TokenStream tokenStream = expertRouterAgent.ask("I broke my leg what should I do");
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        tokenStream
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String story = futureAnswer.get(60, SECONDS);
        ChatResponse response = futureResponse.get(60, SECONDS);

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void part_streaming_agent_in_conditional_workflow() throws Exception {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpert medicalExpert = AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .outputKey("response")
                .build();
        LegalExpertForStreaming legalExpert = AgenticServices.agentBuilder(Agents.LegalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        TechnicalExpertForStreaming technicalExpert = AgenticServices.agentBuilder(TechnicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();

        final ConditionalAgentService<UntypedAgent> conditionalAgentService = AgenticServices.conditionalBuilder();
        conditionalAgentService
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .outputKey("response");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> conditionalAgentService.build())
                .withMessage(
                        "Part of the sub-agents return TokenStream, it needs all agents have the same return type.");
    }

    @Test
    void all_streaming_agent_not_same_output_conditional_workflow() throws Exception {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpertForStreaming medicalExpert = AgenticServices.agentBuilder(MedicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        LegalExpertForStreaming legalExpert = AgenticServices.agentBuilder(Agents.LegalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        TechnicalExpertForStreaming technicalExpert = AgenticServices.agentBuilder(TechnicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response1")
                .build();

        final ConditionalAgentService<UntypedAgent> conditionalAgentService = AgenticServices.conditionalBuilder();
        conditionalAgentService
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .outputKey("response");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> conditionalAgentService.build())
                .withMessage("It needs all agents in the conditional workflow have the same outputKey.");
    }

    @Test
    void all_streaming_agent_not_same_output2_conditional_workflow() throws Exception {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        MedicalExpertForStreaming medicalExpert = AgenticServices.agentBuilder(MedicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        LegalExpertForStreaming legalExpert = AgenticServices.agentBuilder(Agents.LegalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();
        TechnicalExpertForStreaming technicalExpert = AgenticServices.agentBuilder(TechnicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build();

        final ConditionalAgentService<UntypedAgent> conditionalAgentService = AgenticServices.conditionalBuilder();
        conditionalAgentService
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .outputKey("response1");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> conditionalAgentService.build())
                .withMessage("The agents and the workflow should have the same outputKey.");
    }

    @Test
    void streaming_agent_in_supervisor() throws Exception {
        MedicalExpert medicalExpert = AgenticServices.agentBuilder(MedicalExpert.class)
                .chatModel(baseModel())
                .build();
        LegalExpert legalExpert = AgenticServices.agentBuilder(LegalExpert.class)
                .chatModel(baseModel())
                .build();
        TechnicalExpertForStreaming technicalExpert = AgenticServices.agentBuilder(TechnicalExpertForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .build();

        final SupervisorAgentService<SupervisorAgent> supervisorAgentService = AgenticServices.supervisorBuilder();
        supervisorAgentService
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .subAgents(medicalExpert, legalExpert, technicalExpert);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> supervisorAgentService.build())
                .withMessage("Agent cannot be used as a sub-agent because it returns TokenStream.");
    }
}
