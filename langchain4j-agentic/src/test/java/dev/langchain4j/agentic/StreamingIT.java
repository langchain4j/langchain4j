package dev.langchain4j.agentic;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.streamingBaseModel;
import static dev.langchain4j.agentic.observability.HtmlReportGenerator.generateReport;
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

import java.nio.file.Path;
import java.util.List;
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

    @Test
    void streaming_sequence_with_tools_tests() {
        GenerateSearchAgent generateSearchAgent = AgenticServices.agentBuilder(GenerateSearchAgent.class)
                .tools(new ArticleSearchTool())
                .chatModel(baseModel())
                .build();

        ArticleSearchAgent articleSearchAgent = AgenticServices.agentBuilder(ArticleSearchAgent.class)
                .tools(new ArticleSearchTool())
                .streamingChatModel(streamingBaseModel())
                .build();

        ChatAgent agent = AgenticServices.sequenceBuilder(ChatAgent.class)
                .subAgents(generateSearchAgent, articleSearchAgent)
                .build();

        TokenStream tokenStream = agent.chat("asd", "testing article search");
        StringBuilder answerBuilder = new StringBuilder();
        ChatResponse response = waitCompleteResponse(tokenStream, answerBuilder);
        String story = answerBuilder.toString();

        assertThat(story).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);

        AgentMonitor monitor = agent.agentMonitor();
        assertThat(monitor).isNotNull();
//        generateReport(monitor, Path.of("src", "test", "resources", "streaming-agents-exection.html"));
    }

    public record ArticleSearchDto(String keyword, Long authorId, String authorName, Long categoryId,
                                   String categoryName, List<Long> tagIds, String startTime, String endTime,
                                   Integer pageNum, Integer pageSize) {
    }

    public interface ArticleSearchAgent {
        @UserMessage("""
            # Role
            You are a search execution agent. Your sole task is to execute a search using the provided parameters and
            format the results.
            
            # Workflow
            1. **Input**: Receive `searchParameters` from the previous step.
            2. **Action**: **Directly** call the `searchArticle` tool using the provided `searchParameters` exactly as
               they are. **Do not modify, rewrite, or optimize the parameters.**
            3. **Output**: Once results are returned, generate a user-friendly response in **Chinese** following these rules:
                - Format as a numbered Markdown list.
                - Make titles clickable links: `[Title](https://example.com/article/{id})`.
                - Include a brief summary below each title.
                - If no results found, output: "> No articles found matching your query."
                - Do not include JSON, code blocks, or extra explanations. Only output the formatted Markdown.
            
            The search parameters are: {{searchParameters}}
            """)
        @Agent(name = "searchArticle", outputKey = "result")
        TokenStream searchArticle(@V("searchParameters") ArticleSearchDto searchParameters);
    }

    public interface ChatAgent extends MonitoredAgent {
        @UserMessage("{{message}}")
        @Agent(name = "start", outputKey = "result")
        TokenStream chat(@MemoryId String memoryId, @V("message") String message);
    }

    public interface GenerateSearchAgent {

        @SystemMessage("""
            Based on the user input, generate a structured query parameter object in valid JSON format.
            
            Strictly follow these rules:
            - Only include the fields I have defined; do not add any extra fields.
            - For optional fields that are uncertain or not mentioned, set their value to null.
            - Do not include comments, explanations, markdown, or any text outside the JSON.
            - Output must be valid JSON and nothing else.
            
            """)
        @UserMessage("{{message}}")
        @Agent(name = "generateSearchDto", outputKey = "searchParameters")
        ArticleSearchDto generateSearchDto(@V("message") String message);

    }

    public class ArticleSearchTool {

        @Tool(name = "searchArticle", value = "Search for articles")
        public List<String> searchArticle(ArticleSearchDto articleSearchDto) {
            return List.of();
        }


        @Tool(name = "listAllCategory", value = "List all article categories")
        public List<String> listAllCategory() {
            return List.of("categoryName: test, id:1");
        }

        @Tool(name = "listAllTag", value = "List all article tags")
        public List<String> listAllTag() {
            return List.of("tag1", "tag2", "tag3");
        }
    }
}
