package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

class BedrockStrictToolsTest {

    private final TestableBedrockChatModel model = new TestableBedrockChatModel();

    @Test
    void should_set_strict_when_request_parameters_enable_strict_tools() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(true));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isTrue();
        assertThat(inputSchema(toolSpecification).get("additionalProperties").asBoolean())
                .isFalse();
    }

    @Test
    void should_not_set_strict_by_default() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(null));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isNull();
    }

    @Test
    void should_not_set_strict_when_request_parameters_disable_strict_tools() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(false));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isNull();
    }

    @Test
    void per_tool_strict_true_should_override_model_level_false() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(false, toolSpecification(true)));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isTrue();
        assertThat(inputSchema(toolSpecification).get("additionalProperties").asBoolean())
                .isFalse();
    }

    @Test
    void per_tool_strict_false_should_override_model_level_true() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(true, toolSpecification(false)));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isNull();
        assertThat(inputSchema(toolSpecification)).doesNotContainKey("additionalProperties");
    }

    @Test
    void per_tool_strict_false_should_leave_open_map_schema_non_strict_when_model_level_true() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(true, mapToolSpecification(false)));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isNull();

        Map<String, Document> inputSchema = inputSchema(toolSpecification);
        assertThat(inputSchema).doesNotContainKey("additionalProperties");
        assertThat(inputSchema.get("properties").asMap().get("ages").asMap()).doesNotContainKey("additionalProperties");
    }

    @Test
    void per_tool_strict_null_should_fall_back_to_model_level() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(true, toolSpecification(null)));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isTrue();
        assertThat(inputSchema(toolSpecification).get("additionalProperties").asBoolean())
                .isFalse();
    }

    @Test
    void should_apply_strict_tools_builder_setting_to_default_request_parameters() {
        TestableBedrockChatModel model = new TestableBedrockChatModel(new TestBuilder().strictTools(true));

        assertThat(model.defaultParameters().strictTools()).isTrue();
    }

    @Test
    void should_apply_builder_strict_tools_when_chat_request_uses_default_parameters() {
        TestableBedrockChatModel model = new TestableBedrockChatModel(new TestBuilder().strictTools(true));

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .toolSpecifications(toolSpecification(null))
                .build());

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                model.lastToolConfiguration().tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isTrue();
        assertThat(inputSchema(toolSpecification).get("additionalProperties").asBoolean())
                .isFalse();
    }

    @Test
    void should_apply_strict_tools_default_request_parameters() {
        BedrockChatRequestParameters defaultRequestParameters =
                BedrockChatRequestParameters.builder().strictTools(true).build();

        TestableBedrockChatModel model =
                new TestableBedrockChatModel(new TestBuilder().defaultRequestParameters(defaultRequestParameters));

        assertThat(model.defaultParameters().strictTools()).isTrue();
    }

    @Test
    void should_prefer_builder_strict_tools_over_default_request_parameters() {
        BedrockChatRequestParameters defaultRequestParameters =
                BedrockChatRequestParameters.builder().strictTools(true).build();

        TestableBedrockChatModel model = new TestableBedrockChatModel(new TestBuilder()
                .defaultRequestParameters(defaultRequestParameters)
                .strictTools(false));

        assertThat(model.defaultParameters().strictTools()).isFalse();
    }

    private static ChatRequest chatRequest(Boolean strictTools) {
        return chatRequest(strictTools, toolSpecification(null));
    }

    private static ChatRequest chatRequest(Boolean strictTools, ToolSpecification... toolSpecifications) {
        return ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(BedrockChatRequestParameters.builder()
                        .modelName("anthropic.claude-3-5-sonnet-20241022-v2:0")
                        .strictTools(strictTools)
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    private static ToolSpecification toolSpecification(Boolean strict) {
        return ToolSpecification.builder()
                .name("get_weather")
                .description("Get weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .addProperty(
                                "location",
                                JsonObjectSchema.builder()
                                        .addStringProperty("country")
                                        .required("country")
                                        .build())
                        .required("city")
                        .build())
                .strict(strict)
                .build();
    }

    private static ToolSpecification mapToolSpecification(Boolean strict) {
        return ToolSpecification.builder()
                .name("process_ages")
                .description("Process ages")
                .parameters(JsonObjectSchema.builder()
                        .addProperty(
                                "ages",
                                JsonObjectSchema.builder()
                                        .description("map from name to age")
                                        .build())
                        .required("ages")
                        .build())
                .strict(strict)
                .build();
    }

    private static Map<String, Document> inputSchema(
            software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification) {
        return toolSpecification.inputSchema().json().asMap();
    }

    private static class TestableBedrockChatModel extends AbstractBedrockChatModel implements ChatModel {

        private ToolConfiguration lastToolConfiguration;

        TestableBedrockChatModel() {
            super(new TestBuilder());
        }

        TestableBedrockChatModel(TestBuilder builder) {
            super(builder);
        }

        BedrockChatRequestParameters defaultParameters() {
            return defaultRequestParameters;
        }

        ToolConfiguration toolConfiguration(ChatRequest chatRequest) {
            return extractToolConfigurationFrom(chatRequest);
        }

        ToolConfiguration lastToolConfiguration() {
            return lastToolConfiguration;
        }

        @Override
        public BedrockChatRequestParameters defaultRequestParameters() {
            return defaultRequestParameters;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            lastToolConfiguration = extractToolConfigurationFrom(chatRequest);
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    private static class TestBuilder extends AbstractBedrockChatModel.AbstractBuilder<TestBuilder> {

        @Override
        public TestBuilder self() {
            return this;
        }
    }
}
