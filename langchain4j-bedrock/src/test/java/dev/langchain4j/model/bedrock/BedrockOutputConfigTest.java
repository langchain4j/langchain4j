package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;

class BedrockOutputConfigTest {

    @Test
    void should_return_null_output_config_when_parameters_are_null() {
        assertThat(AbstractBedrockChatModel.outputConfigFrom(null)).isNull();
    }

    @Test
    void should_return_null_output_config_when_response_format_is_not_set() {
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();

        assertThat(AbstractBedrockChatModel.outputConfigFrom(parameters)).isNull();
    }

    @Test
    void should_return_null_output_config_when_response_format_is_text() {
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.TEXT)
                .build();

        assertThat(AbstractBedrockChatModel.outputConfigFrom(parameters)).isNull();
    }

    @Test
    void should_return_null_output_config_when_json_schema_is_missing() {
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(
                        ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .build();

        assertThat(AbstractBedrockChatModel.outputConfigFrom(parameters)).isNull();
    }

    @Test
    void should_return_null_output_config_when_json_schema_root_is_missing() {
        JsonSchema jsonSchema = JsonSchema.builder().name("missing_root").build();
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().responseFormat(jsonSchema).build();

        assertThat(AbstractBedrockChatModel.outputConfigFrom(parameters)).isNull();
    }

    @Test
    void should_build_output_config_from_structured_json_schema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("city_schema")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("city", JsonStringSchema.builder().build())
                        .required("city")
                        .build())
                .build();
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().responseFormat(jsonSchema).build();

        var outputConfig = AbstractBedrockChatModel.outputConfigFrom(parameters);

        assertThat(outputConfig).isNotNull();
        assertThat(outputConfig.textFormat().type()).isEqualTo(OutputFormatType.JSON_SCHEMA);
        assertThat(outputConfig.textFormat().structure().jsonSchema().name()).isEqualTo("city_schema");
        assertThat(outputConfig.textFormat().structure().jsonSchema().schema())
                .contains("\"type\" : \"object\"")
                .contains("\"city\"");
    }

    @Test
    void should_build_output_config_from_raw_json_schema() {
        String rawSchema =
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}";
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("raw_city_schema")
                .rootElement(JsonRawSchema.from(rawSchema))
                .build();
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().responseFormat(jsonSchema).build();

        var outputConfig = AbstractBedrockChatModel.outputConfigFrom(parameters);

        assertThat(outputConfig).isNotNull();
        assertThat(outputConfig.textFormat().type()).isEqualTo(OutputFormatType.JSON_SCHEMA);
        assertThat(outputConfig.textFormat().structure().jsonSchema().name()).isEqualTo("raw_city_schema");
        assertThat(outputConfig.textFormat().structure().jsonSchema().schema()).isEqualTo(rawSchema);
    }

    @Test
    void should_allow_json_response_format_with_schema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("city", JsonStringSchema.builder().build())
                        .build())
                .build();
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().responseFormat(jsonSchema).build();

        assertThatCode(() -> AbstractBedrockChatModel.validate(parameters)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_schemaless_json_response_format() {
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(
                        ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .build();

        assertThatThrownBy(() -> AbstractBedrockChatModel.validate(parameters))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("Schemaless JSON response format");
    }
}
