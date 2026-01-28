package dev.langchain4j.model.azure;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.openai.responses.models.ResponsesResponse;
import com.azure.core.util.BinaryData;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

class AzureOpenAiResponsesResponseParserTest {

    @Test
    void should_extract_reasoning_summary_and_text() {
        String json = "{"
                + "\"id\":\"resp_1\","
                + "\"object\":\"response\","
                + "\"status\":\"completed\","
                + "\"model\":\"gpt-4o-mini\","
                + "\"output\":["
                + "{\"id\":\"rs_1\",\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"Reasoning summary\"}]},"
                + "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hello\"}]}"
                + "],"
                + "\"usage\":{\"input_tokens\":1,\"output_tokens\":2,\"total_tokens\":3}"
                + "}";

        ResponsesResponse response = BinaryData.fromString(json).toObject(ResponsesResponse.class);
        Response<AiMessage> parsed = InternalAzureOpenAiResponsesHelper.toResponse(response, null);

        assertThat(parsed.content().text()).isEqualTo("Hello");
        assertThat(parsed.content().thinking()).isEqualTo("Reasoning summary");
        assertThat(parsed.tokenUsage().inputTokenCount()).isEqualTo(1);
        assertThat(parsed.tokenUsage().outputTokenCount()).isEqualTo(2);
        assertThat(parsed.tokenUsage().totalTokenCount()).isEqualTo(3);
        assertThat(parsed.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_handle_missing_reasoning_summary() {
        String json = "{"
                + "\"id\":\"resp_2\","
                + "\"object\":\"response\","
                + "\"status\":\"completed\","
                + "\"model\":\"gpt-4o-mini\","
                + "\"output\":["
                + "{\"id\":\"msg_2\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\","
                + "\"content\":[{\"type\":\"output_text\",\"text\":\"Hello\"}]}"
                + "],"
                + "\"usage\":{"
                + "\"input_tokens\":1,"
                + "\"output_tokens\":2,"
                + "\"total_tokens\":3,"
                + "\"output_tokens_details\":{\"reasoning_tokens\":42}"
                + "}"
                + "}";

        ResponsesResponse response = BinaryData.fromString(json).toObject(ResponsesResponse.class);
        Response<AiMessage> parsed = InternalAzureOpenAiResponsesHelper.toResponse(response, null);

        assertThat(parsed.content().text()).isEqualTo("Hello");
        assertThat(parsed.content().thinking()).isNull();
    }
}
