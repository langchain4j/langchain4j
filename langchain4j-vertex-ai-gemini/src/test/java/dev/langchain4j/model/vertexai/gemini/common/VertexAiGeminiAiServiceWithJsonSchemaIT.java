package dev.langchain4j.model.vertexai.gemini.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

class VertexAiGeminiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(
                VertexAiGeminiChatModel.builder()
                        .project(System.getenv("GCP_PROJECT_ID"))
                        .location(System.getenv("GCP_LOCATION"))
                        .modelName("gemini-2.0-flash")
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .temperature(0.0f)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
        );
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_nested_pojo(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_list_of_enums(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_set_of_primitives(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_list_of_primitives(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_array_of_primitives(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_array_of_enums(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_local_date_time_fields(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_enum(ChatModel model) {
    }

    @Disabled("Gemini cannot do it properly")
    @Override
    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_set_of_enums(ChatModel model) {
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
