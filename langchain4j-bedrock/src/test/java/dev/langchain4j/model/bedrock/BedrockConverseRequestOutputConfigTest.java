package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;

class BedrockConverseRequestOutputConfigTest {

    private static final String MODEL_ID = "us.amazon.nova-lite-v1:0";

    @Test
    void should_include_output_config_in_converse_request_when_structured_json_is_used() throws Exception {
        BedrockChatModel model = BedrockChatModel.builder()
                .modelId(MODEL_ID)
                .client(mock(BedrockRuntimeClient.class))
                .build();

        Method buildConverseRequest =
                BedrockChatModel.class.getDeclaredMethod("buildConverseRequest", ChatRequest.class);
        buildConverseRequest.setAccessible(true);

        ConverseRequest converseRequest =
                (ConverseRequest) buildConverseRequest.invoke(model, chatRequestWithStructuredJsonResponseFormat());

        assertThat(converseRequest.outputConfig()).isNotNull();
        assertThat(converseRequest.outputConfig().textFormat().type()).isEqualTo(OutputFormatType.JSON_SCHEMA);
        assertThat(converseRequest
                        .outputConfig()
                        .textFormat()
                        .structure()
                        .jsonSchema()
                        .name())
                .isEqualTo("city_schema");
    }

    @Test
    void should_include_output_config_in_converse_stream_request_when_structured_json_is_used() throws Exception {
        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(MODEL_ID)
                .client(mock(BedrockRuntimeAsyncClient.class))
                .build();

        Method buildConverseStreamRequest =
                BedrockStreamingChatModel.class.getDeclaredMethod("buildConverseStreamRequest", ChatRequest.class);
        buildConverseStreamRequest.setAccessible(true);

        ConverseStreamRequest converseStreamRequest = (ConverseStreamRequest)
                buildConverseStreamRequest.invoke(model, chatRequestWithStructuredJsonResponseFormat());

        assertThat(converseStreamRequest.outputConfig()).isNotNull();
        assertThat(converseStreamRequest.outputConfig().textFormat().type()).isEqualTo(OutputFormatType.JSON_SCHEMA);
        assertThat(converseStreamRequest
                        .outputConfig()
                        .textFormat()
                        .structure()
                        .jsonSchema()
                        .name())
                .isEqualTo("city_schema");
    }

    private static ChatRequest chatRequestWithStructuredJsonResponseFormat() {
        BedrockChatRequestParameters parameters = BedrockChatRequestParameters.builder()
                .modelName(MODEL_ID)
                .responseFormat(cityJsonSchema())
                .build();

        return ChatRequest.builder()
                .messages(UserMessage.from("Extract city from user input"))
                .parameters(parameters)
                .build();
    }

    private static JsonSchema cityJsonSchema() {
        return JsonSchema.builder()
                .name("city_schema")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("city", JsonStringSchema.builder().build())
                        .required("city")
                        .build())
                .build();
    }
}
