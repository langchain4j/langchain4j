package dev.langchain4j.model.workersai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkersAiTextCompletionResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void should_deserialize_response_with_usage_and_finish_reason() throws Exception {
        String json = "{\n" + "  \"result\": {\n"
                + "    \"response\": \"Hello World\",\n"
                + "    \"usage\": {\n"
                + "      \"prompt_tokens\": 10,\n"
                + "      \"completion_tokens\": 20,\n"
                + "      \"total_tokens\": 30\n"
                + "    },\n"
                + "    \"finish_reason\": \"stop\"\n"
                + "  },\n"
                + "  \"success\": true,\n"
                + "  \"errors\": [],\n"
                + "  \"messages\": []\n"
                + "}";

        WorkersAiTextCompletionResponse response = mapper.readValue(json, WorkersAiTextCompletionResponse.class);
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        WorkersAiTextCompletionResponse.TextResponse textResponse = response.getResult();
        assertThat(textResponse).isNotNull();
        assertThat(textResponse.getResponse()).isEqualTo("Hello World");
        assertThat(textResponse.getFinishReason()).isEqualTo("stop");

        WorkersAiTextCompletionResponse.Usage usage = textResponse.getUsage();
        assertThat(usage).isNotNull();
        assertThat(usage.getPromptTokens()).isEqualTo(10);
        assertThat(usage.getCompletionTokens()).isEqualTo(20);
        assertThat(usage.getTotalTokens()).isEqualTo(30);
    }

    @Test
    void should_ignore_unknown_fields_in_future() throws Exception {
        String json = "{\n" + "  \"result\": {\n"
                + "    \"response\": \"Hello\",\n"
                + "    \"meta_info\": \"some new field\"\n"
                + "  },\n"
                + "  \"success\": true\n"
                + "}";

        WorkersAiTextCompletionResponse response = mapper.readValue(json, WorkersAiTextCompletionResponse.class);
        assertThat(response.getResult().getResponse()).isEqualTo("Hello");
    }
}
