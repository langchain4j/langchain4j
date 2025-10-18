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
import dev.langchain4j.agentic.Agents.CreativeWriter;
import dev.langchain4j.agentic.Agents.LegalExpert;
import dev.langchain4j.agentic.Agents.MedicalExpert;
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

public class StreamingAgentWorkflowIT {

    @Test
    void streaming_agent_only_in_last_sequence_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
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
    void streaming_agent_in_parallel_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
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
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        final LoopAgentService<UntypedAgent> loopAgentService = AgenticServices.loopBuilder();
        loopAgentService.subAgents(creativeWriter, audienceEditor, styleEditor).outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> loopAgentService.build())
                .withMessage("Agent cannot be used as a sub-agent because it returns TokenStream.");
    }

    @Test
    void streaming_agent_in_conditional_workflow() throws Exception {
        CreativeWriter creativeWriter = AgenticServices.agentBuilder(CreativeWriter.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .outputKey("story")
                .build();

        StyleEditorForStreaming styleEditor = AgenticServices.agentBuilder(StyleEditorForStreaming.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        final ConditionalAgentService<UntypedAgent> conditionalAgentService = AgenticServices.conditionalBuilder();
        conditionalAgentService
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> conditionalAgentService.build())
                .withMessage("Agent cannot be used as a sub-agent because it returns TokenStream.");
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
