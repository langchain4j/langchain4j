package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiResponsesToolExecutionResultMessageTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String RESPONSE = """
            {
              "id": "resp_test",
              "model": "gpt-5.4-mini",
              "object": "response",
              "status": "completed",
              "output": [
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "content": [{"type": "output_text", "text": "Done"}]
                }
              ],
              "usage": {"input_tokens": 10, "output_tokens": 1, "total_tokens": 11}
            }
            """;

    private static final ToolExecutionRequest TOOL_EXECUTION_REQUEST = ToolExecutionRequest.builder()
            .id("call_123")
            .name("getReport")
            .arguments("{}")
            .build();

    private MockHttpClient mockHttpClient;
    private OpenAiResponsesChatModel model;

    @BeforeEach
    void setUp() {
        mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());
        model = OpenAiResponsesChatModel.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName("gpt-5.4-mini")
                .build();
    }

    @Test
    void should_send_pdf_url_in_tool_result_as_input_file() throws Exception {
        chat(toolResult()
                .contents(PdfFileContent.from(URI.create("https://example.com/report.pdf")))
                .build());

        JsonNode output = functionCallOutput().get("output");
        assertThat(output).hasSize(1);
        assertThat(output.get(0).get("type").asText()).isEqualTo("input_file");
        assertThat(output.get(0).get("file_url").asText()).isEqualTo("https://example.com/report.pdf");
        assertThat(output.get(0).has("file_data")).isFalse();
    }

    @Test
    void should_send_base64_pdf_in_tool_result_as_input_file() throws Exception {
        chat(toolResult()
                .contents(PdfFileContent.from(PdfFile.builder()
                        .base64Data("QUJD")
                        .mimeType("application/pdf")
                        .build()))
                .build());

        JsonNode output = functionCallOutput().get("output");
        assertThat(output).hasSize(1);
        assertThat(output.get(0).get("type").asText()).isEqualTo("input_file");
        assertThat(output.get(0).get("file_data").asText()).isEqualTo("data:application/pdf;base64,QUJD");
        assertThat(output.get(0).get("filename").asText()).isEqualTo("pdf_file");
        assertThat(output.get(0).has("file_url")).isFalse();
    }

    @Test
    void should_keep_order_of_text_image_and_pdf_in_tool_result() throws Exception {
        chat(toolResult()
                .contents(
                        TextContent.from("Here is the report"),
                        ImageContent.from("QUJD", "image/png"),
                        PdfFileContent.from(URI.create("https://example.com/report.pdf")))
                .build());

        JsonNode functionCallOutput = functionCallOutput();
        assertThat(functionCallOutput.get("type").asText()).isEqualTo("function_call_output");
        assertThat(functionCallOutput.get("call_id").asText()).isEqualTo("call_123");

        JsonNode output = functionCallOutput.get("output");
        assertThat(output).hasSize(3);
        assertThat(output.get(0).get("type").asText()).isEqualTo("input_text");
        assertThat(output.get(0).get("text").asText()).isEqualTo("Here is the report");
        assertThat(output.get(1).get("type").asText()).isEqualTo("input_image");
        assertThat(output.get(2).get("type").asText()).isEqualTo("input_file");
    }

    @Test
    void should_send_breakpoint_on_trailing_pdf_in_tool_result() throws Exception {
        chat(toolResult()
                .contents(
                        TextContent.from("Here is the report"),
                        PdfFileContent.from(URI.create("https://example.com/report.pdf")))
                .attributes(
                        Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
                .build());

        JsonNode output = functionCallOutput().get("output");
        assertThat(output).hasSize(2);
        assertThat(output.get(0).has("prompt_cache_breakpoint")).isFalse();
        assertThat(output.get(1).get("type").asText()).isEqualTo("input_file");
        assertThat(output.get(1).get("prompt_cache_breakpoint").get("mode").asText())
                .isEqualTo("explicit");
    }

    @Test
    void should_fail_when_tool_result_contains_unsupported_content() {
        ToolExecutionResultMessage toolResult = toolResult()
                .contents(
                        TextContent.from("Here is the recording"),
                        AudioContent.from(Audio.builder()
                                .base64Data("AAAA")
                                .mimeType("audio/mp3")
                                .build()))
                .build();

        assertThatThrownBy(() -> chat(toolResult))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Unsupported content type in tool result: dev.langchain4j.data.message.AudioContent"
                        + ". Only TextContent, ImageContent, and PdfFileContent are supported.");
    }

    private static ToolExecutionResultMessage.Builder toolResult() {
        return ToolExecutionResultMessage.builder().id("call_123").toolName("getReport");
    }

    private void chat(ToolExecutionResultMessage toolResult) {
        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Get the report"), AiMessage.from(TOOL_EXECUTION_REQUEST), toolResult)
                .build());
    }

    private JsonNode functionCallOutput() throws Exception {
        JsonNode input = OBJECT_MAPPER.readTree(mockHttpClient.request().body()).get("input");
        return input.get(input.size() - 1);
    }
}
