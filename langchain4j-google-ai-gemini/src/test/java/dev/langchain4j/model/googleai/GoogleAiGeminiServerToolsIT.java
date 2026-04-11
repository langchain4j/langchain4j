package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiServerToolsIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String GEMINI_3_FLASH_MODEL = "gemini-3-flash-preview";
    private static final String GEMINI_2_5_FLASH_MODEL = "gemini-2.5-flash";

    @Test
    void should_execute_code_with_server_tools() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(GEMINI_3_FLASH_MODEL)
                .serverTools(GoogleAiGeminiServerTool.builder()
                        .type("code_execution")
                        .build())
                .includeCodeExecutionOutput(true)
                .returnServerToolResults(true)
                .build();

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(
                        "Calculate fibonacci(13). Use Python code execution and give me the numeric answer."))
                .build());

        assertThat(response.aiMessage().text()).contains("233");
        assertThat(response.aiMessage().attributes()).containsKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);

        @SuppressWarnings("unchecked")
        List<GoogleAiGeminiServerToolResult> results = (List<GoogleAiGeminiServerToolResult>)
                response.aiMessage().attributes().get(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);

        assertThat(results).extracting(GoogleAiGeminiServerToolResult::type).contains("code_execution_tool_result");
    }

    @Test
    void should_execute_code_with_server_tools_in_streaming_mode() {
        GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(GEMINI_3_FLASH_MODEL)
                .serverTools(GoogleAiGeminiServerTool.builder()
                        .type("code_execution")
                        .build())
                .includeCodeExecutionOutput(true)
                .returnServerToolResults(true)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(
                                "Calculate fibonacci(13). Use Python code execution and give me the numeric answer."))
                        .build(),
                handler);

        ChatResponse response = handler.get();

        assertThat(response.aiMessage().text()).contains("233");
        assertServerToolResultPresent(response, "code_execution_tool_result");
    }

    @Test
    void should_use_google_search_with_server_tools() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(GEMINI_2_5_FLASH_MODEL)
                .serverTools(
                        GoogleAiGeminiServerTool.builder().type("google_search").build())
                .returnServerToolResults(true)
                .build();

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from(
                        "Use the Google Search tool to answer this question: what is the capital of France? "
                                + "Reply with only the city name."))
                .build());

        assertThat(response.aiMessage().text()).containsIgnoringCase("Paris");
        assertServerToolResultPresent(response, "google_search_tool_result");
    }

    @Test
    void should_use_url_context_with_server_tools() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(GEMINI_3_FLASH_MODEL)
                .serverTools(
                        GoogleAiGeminiServerTool.builder().type("url_context").build())
                .returnServerToolResults(true)
                .build();

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(
                        UserMessage.from(
                                "Use URL context on https://docs.langchain4j.dev/ and tell me what project this documentation is for."))
                .build());

        assertThat(response.aiMessage().text()).containsIgnoringCase("LangChain4j");
        assertServerToolResultPresent(response, "url_context_tool_result");
    }

    @Test
    void should_use_google_maps_with_server_tools() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(GEMINI_2_5_FLASH_MODEL)
                .serverTools(GoogleAiGeminiServerTool.builder()
                        .type("google_maps")
                        .addAttribute("enable_widget", true)
                        .build())
                .returnServerToolResults(true)
                .build();

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Use Google Maps grounding to name one famous landmark in Paris."))
                .build());

        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.aiMessage().text()).containsIgnoringCase("Paris");
        assertServerToolResultPresent(response, "google_maps_tool_result");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertServerToolResultPresent(ChatResponse response, String expectedType) {
        assertThat(response.aiMessage().attributes()).containsKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
        List<GoogleAiGeminiServerToolResult> results = (List<GoogleAiGeminiServerToolResult>)
                response.aiMessage().attributes().get(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
        assertThat(results).extracting(GoogleAiGeminiServerToolResult::type).contains(expectedType);
    }
}
