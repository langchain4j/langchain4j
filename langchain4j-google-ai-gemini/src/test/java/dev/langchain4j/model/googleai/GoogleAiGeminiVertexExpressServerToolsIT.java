package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiVertexExpressServerToolsIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String VERTEX_EXPRESS_BASE_URL = getOrDefault(
            System.getenv("GOOGLE_AI_GEMINI_VERTEX_EXPRESS_BASE_URL"),
            "https://aiplatform.googleapis.com/v1/publishers/google");
    private static final String GEMINI_3_1_PRO_PREVIEW_MODEL = "gemini-3.1-pro-preview";

    @Test
    void should_execute_code_with_server_tools_on_vertex_express() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .baseUrl(VERTEX_EXPRESS_BASE_URL)
                .modelName(GEMINI_3_1_PRO_PREVIEW_MODEL)
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
        assertServerToolResultPresent(response, "code_execution_tool_result");
    }

    @Test
    void should_use_url_context_with_server_tools_on_vertex_express() {
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .baseUrl(VERTEX_EXPRESS_BASE_URL)
                .modelName(GEMINI_3_1_PRO_PREVIEW_MODEL)
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

    @SuppressWarnings("unchecked")
    private static void assertServerToolResultPresent(ChatResponse response, String expectedType) {
        assertThat(response.aiMessage().attributes()).containsKey(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
        List<GoogleAiGeminiServerToolResult> results = (List<GoogleAiGeminiServerToolResult>)
                response.aiMessage().attributes().get(GeminiServerToolsMapper.SERVER_TOOL_RESULTS_KEY);
        assertThat(results).extracting(GoogleAiGeminiServerToolResult::type).contains(expectedType);
    }
}
