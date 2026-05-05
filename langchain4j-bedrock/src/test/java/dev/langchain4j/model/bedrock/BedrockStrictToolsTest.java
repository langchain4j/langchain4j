package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

class BedrockStrictToolsTest {

    private final TestableBedrockChatModel model = new TestableBedrockChatModel();

    @Test
    void should_set_strict_on_tool_specification() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(true));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isTrue();
    }

    @Test
    void should_not_set_strict_on_tool_specification_by_default() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(null));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isNull();
    }

    @Test
    void should_not_set_strict_on_tool_specification_when_disabled() {
        ToolConfiguration toolConfiguration = model.toolConfiguration(chatRequest(false));

        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification toolSpecification =
                toolConfiguration.tools().get(0).toolSpec();
        assertThat(toolSpecification.strict()).isNull();
    }

    @Test
    void should_apply_strict_tools_builder_setting_to_default_request_parameters() {
        TestableBedrockChatModel model = new TestableBedrockChatModel(new TestBuilder().strictTools(true));

        assertThat(model.defaultParameters().strictTools()).isTrue();
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
        return ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(BedrockChatRequestParameters.builder()
                        .modelName("anthropic.claude-3-5-sonnet-20241022-v2:0")
                        .strictTools(strictTools)
                        .toolSpecifications(toolSpecification())
                        .build())
                .build();
    }

    private static ToolSpecification toolSpecification() {
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
                .build();
    }

    private static class TestableBedrockChatModel extends AbstractBedrockChatModel {

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
    }

    private static class TestBuilder extends AbstractBedrockChatModel.AbstractBuilder<TestBuilder> {

        @Override
        public TestBuilder self() {
            return this;
        }
    }
}
