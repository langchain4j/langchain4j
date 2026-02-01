package dev.langchain4j.agentic;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.streamingBaseModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agentic.Agents.ReviewedWriter;
import dev.langchain4j.agentic.Agents.AudienceEditor;
import dev.langchain4j.agentic.Agents.CategoryRouter;
import dev.langchain4j.agentic.Agents.ExpertRouterAgent;
import dev.langchain4j.agentic.StreamingAgents.StreamingCreativeWriter;
import dev.langchain4j.agentic.StreamingAgents.StreamingAudienceEditor;
import dev.langchain4j.agentic.StreamingAgents.StreamingStyleEditor;
import dev.langchain4j.agentic.StreamingAgents.StreamingReviewedWriter;
import dev.langchain4j.agentic.StreamingAgents.StreamingExpertRouterAgent;
import dev.langchain4j.agentic.StreamingAgents.StreamingMedicalExpert;
import dev.langchain4j.agentic.StreamingAgents.StreamingLegalExpert;
import dev.langchain4j.agentic.StreamingAgents.StreamingTechnicalExpert;
import dev.langchain4j.agentic.StreamingAgents.StreamingStoryCreator;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class StreamingIT {

    @Test
    void streaming_single_agent_test() {
        StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        TokenStream tokenStream = creativeWriter.generateStory("dragons and wizards");

        StringBuilder answerBuilder = new StringBuilder();
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String story = answerBuilder.toString();

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    private static ChatResponse waitCompleteResponse(TokenStream tokenStream, StringBuilder answerBuilder) {
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        tokenStream
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    assertThat(response.aiMessage().text()).isEqualTo(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureResponse::completeExceptionally)
                .start();

        try {
            return futureResponse.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void streaming_sequence_test() {
        StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingAudienceEditor audienceEditor = AgenticServices.agentBuilder(StreamingAudienceEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingStyleEditor styleEditor = AgenticServices.agentBuilder(StreamingStyleEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingReviewedWriter novelCreator = AgenticServices.sequenceBuilder(StreamingReviewedWriter.class)
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        TokenStream tokenStream = novelCreator.writeStory("dragons and wizards", "young adults", "fantasy");

        StringBuilder answerBuilder = new StringBuilder();
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String story = answerBuilder.toString();

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void untyped_streaming_sequence_test() {
        StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingAudienceEditor audienceEditor = AgenticServices.agentBuilder(StreamingAudienceEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingStyleEditor styleEditor = AgenticServices.agentBuilder(StreamingStyleEditor.class)
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
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String story = answerBuilder.toString();

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void mix_normal_and_streaming_sequence_test() {
        StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        AudienceEditor audienceEditor = AgenticServices.agentBuilder(AudienceEditor.class)
                .chatModel(baseModel())
                .outputKey("story")
                .build();

        StreamingStyleEditor styleEditor = AgenticServices.agentBuilder(StreamingStyleEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingReviewedWriter novelCreator = AgenticServices.sequenceBuilder(StreamingReviewedWriter.class)
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        TokenStream tokenStream = novelCreator.writeStory("dragons and wizards", "young adults", "fantasy");

        StringBuilder answerBuilder = new StringBuilder();
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String story = answerBuilder.toString();

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void streaming_agents_with_non_streaming_sequence_test() {
        StreamingCreativeWriter creativeWriter = AgenticServices.agentBuilder(StreamingCreativeWriter.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingAudienceEditor audienceEditor = AgenticServices.agentBuilder(StreamingAudienceEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        StreamingStyleEditor styleEditor = AgenticServices.agentBuilder(StreamingStyleEditor.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("story")
                .build();

        ReviewedWriter novelCreator = AgenticServices.sequenceBuilder(ReviewedWriter.class)
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .outputKey("story")
                .build();

        String story = novelCreator.writeStory("dragons and wizards", "young adults", "fantasy");
        assertThat(story).isNotBlank();
    }

    @Test
    void streaming_conditional_agents_tests() {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        StreamingMedicalExpert medicalExpert = spy(AgenticServices.agentBuilder(StreamingMedicalExpert.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build());
        StreamingLegalExpert legalExpert = spy(AgenticServices.agentBuilder(StreamingLegalExpert.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build());
        StreamingTechnicalExpert technicalExpert = spy(AgenticServices.agentBuilder(StreamingTechnicalExpert.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build());

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", Agents.RequestCategory.UNKNOWN) == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", Agents.RequestCategory.UNKNOWN) == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .build();

        StreamingExpertRouterAgent agentInstance = AgenticServices.sequenceBuilder(StreamingExpertRouterAgent.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        TokenStream tokenStream = agentInstance.ask("I broke my leg what should I do");
        verify(medicalExpert).medical("I broke my leg what should I do");

        StringBuilder answerBuilder = new StringBuilder();
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String expertResponse = answerBuilder.toString();

        assertThat(expertResponse).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void streaming_conditional_agents_with_non_streaming_sequence_test() {
        CategoryRouter routerAgent = AgenticServices.agentBuilder(CategoryRouter.class)
                .chatModel(baseModel())
                .outputKey("category")
                .build();

        StreamingMedicalExpert medicalExpert = spy(AgenticServices.agentBuilder(StreamingMedicalExpert.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build());
        StreamingLegalExpert legalExpert = spy(AgenticServices.agentBuilder(StreamingLegalExpert.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build());
        StreamingTechnicalExpert technicalExpert = spy(AgenticServices.agentBuilder(StreamingTechnicalExpert.class)
                .streamingChatModel(streamingBaseModel())
                .outputKey("response")
                .build());

        UntypedAgent expertsAgent = AgenticServices.conditionalBuilder()
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", Agents.RequestCategory.UNKNOWN) == Agents.RequestCategory.MEDICAL,
                        medicalExpert)
                .subAgents(
                        agenticScope ->
                                agenticScope.readState("category", Agents.RequestCategory.UNKNOWN) == Agents.RequestCategory.LEGAL,
                        legalExpert)
                .subAgents(
                        agenticScope -> agenticScope.readState("category", Agents.RequestCategory.UNKNOWN)
                                == Agents.RequestCategory.TECHNICAL,
                        technicalExpert)
                .build();

        ExpertRouterAgent agentInstance = AgenticServices.sequenceBuilder(ExpertRouterAgent.class)
                .subAgents(routerAgent, expertsAgent)
                .outputKey("response")
                .build();

        String expertResponse = agentInstance.ask("I broke my leg what should I do");
        verify(medicalExpert).medical("I broke my leg what should I do");
        assertThat(expertResponse).isNotBlank();
    }

    @Test
    void declarative_streaming_sequence_tests() {
        StreamingStoryCreator storyCreator = AgenticServices.createAgenticSystem(StreamingStoryCreator.class);

        TokenStream tokenStream = storyCreator.write("dragons and wizards", "fantasy", "young adults");
        StringBuilder answerBuilder = new StringBuilder();
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String story = answerBuilder.toString();

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }
}
