package dev.langchain4j.model.chat.request;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

class ChatRequestTest {

    @Test
    void should_keep_backward_compatibility() {

        // given
        UserMessage userMessage = UserMessage.from("hi");
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("tool").build();
        ResponseFormat responseFormat = ResponseFormat.JSON;

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .responseFormat(responseFormat)
                .build();

        // then
        assertThat(chatRequest.messages()).containsExactly(userMessage);
        assertThat(chatRequest.toolSpecifications()).containsExactly(toolSpecification);
        assertThat(chatRequest.responseFormat()).isEqualTo(responseFormat);
    }

    @Test
    void should_set_messages_and_request_parameters() {

        // given
        UserMessage userMessage = UserMessage.from("hi");
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("tool").build();
        ResponseFormat responseFormat = ResponseFormat.JSON;
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .responseFormat(responseFormat)
                .build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(parameters)
                .build();

        // then
        assertThat(chatRequest.messages()).containsExactly(userMessage);
        assertThat(chatRequest.parameters()).isEqualTo(parameters);

        assertThat(chatRequest.toolSpecifications()).containsExactly(toolSpecification);
        assertThat(chatRequest.responseFormat()).isEqualTo(responseFormat);
    }

    @Test
    void should_override_response_format_when_both_parameters_and_response_format_are_set() {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(DefaultChatRequestParameters.EMPTY)
                .responseFormat(ResponseFormat.JSON)
                .build();

        assertThat(chatRequest.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void should_override_tool_specifications_when_both_parameters_and_toolSpecifications_are_set() {

        ToolSpecification tool = ToolSpecification.builder().name("tool").build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(DefaultChatRequestParameters.EMPTY)
                .toolSpecifications(tool)
                .build();

        assertThat(chatRequest.toolSpecifications()).containsExactly(tool);
    }

    @Test
    void should_create_chat_request_with_multiple_messages() {
        // given
        UserMessage firstMessage = UserMessage.from("Hello");
        UserMessage secondMessage = UserMessage.from("How are you?");

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(firstMessage, secondMessage).build();

        // then
        assertThat(chatRequest.messages()).containsExactly(firstMessage, secondMessage);
    }

    @Test
    void should_create_chat_request_with_multiple_tool_specifications() {
        // given
        ToolSpecification tool1 = ToolSpecification.builder().name("tool1").build();
        ToolSpecification tool2 = ToolSpecification.builder().name("tool2").build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications(tool1, tool2)
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).containsExactly(tool1, tool2);
    }

    @Test
    void should_create_chat_request_with_only_parameters() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .build();

        // then
        assertThat(chatRequest.parameters()).isEqualTo(parameters);
        assertThat(chatRequest.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void should_handle_null_response_format() {
        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .responseFormat(null)
                .build();

        // then
        assertThat(chatRequest.responseFormat()).isNull();
    }

    @Test
    void should_fail_when_messages_is_null() {
        assertThatThrownBy(() ->
                        ChatRequest.builder().messages((UserMessage[]) null).build())
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void should_preserve_order_of_messages() {
        // given
        UserMessage first = UserMessage.from("first");
        UserMessage second = UserMessage.from("second");
        UserMessage third = UserMessage.from("third");

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(first, second, third).build();

        // then
        assertThat(chatRequest.messages()).containsExactly(first, second, third);
    }

    @Test
    void should_preserve_order_of_tool_specifications() {
        // given
        ToolSpecification toolA = ToolSpecification.builder().name("toolA").build();
        ToolSpecification toolB = ToolSpecification.builder().name("toolB").build();
        ToolSpecification toolC = ToolSpecification.builder().name("toolC").build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications(toolA, toolB, toolC)
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).containsExactly(toolA, toolB, toolC);
    }

    @Test
    void should_handle_empty_tool_specifications_array() {
        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications()
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).isEmpty();
    }

    @Test
    void should_override_response_format_when_both_parameters_and_response_format_are_non_null() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .responseFormat(ResponseFormat.JSON)
                .build();

        // then
        assertThat(chatRequest.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void should_override_tool_specifications_when_both_parameters_and_tool_specifications_are_non_null() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        ToolSpecification tool = ToolSpecification.builder().name("tool").build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .toolSpecifications(tool)
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).containsExactly(tool);
    }

    @Test
    void should_fail_when_no_messages_provided() {
        // when/then
        assertThatThrownBy(() -> ChatRequest.builder().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void should_fail_when_empty_messages_array_provided() {
        // when/then
        assertThatThrownBy(() -> ChatRequest.builder().messages().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void should_override_tool_specifications_from_parameters() {
        // given
        ToolSpecification paramTool =
                ToolSpecification.builder().name("paramTool").build();
        ToolSpecification builderTool =
                ToolSpecification.builder().name("builderTool").build();

        ChatRequestParameters parameters =
                ChatRequestParameters.builder().toolSpecifications(paramTool).build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .toolSpecifications(builderTool)
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).containsExactly(builderTool);
    }

    @Test
    void should_fail_when_messages_are_null() {
        assertThatThrownBy(() ->
                ChatRequest.builder().build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages");
    }

    @Test
    void should_fail_when_messages_are_empty() {
        assertThatThrownBy(() ->
                ChatRequest.builder()
                        .messages(List.of())
                        .build()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages");
    }

    @Test
    void should_override_modelName_when_both_parameters_and_modelName_are_set() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder().modelName("gpt-4").build())
                .modelName("gpt-3.5")
                .build();

        assertThat(chatRequest.modelName()).isEqualTo("gpt-3.5");
    }

    @Test
    void should_override_temperature_when_both_parameters_and_temperature_are_set() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder().temperature(0.5).build())
                .temperature(0.7)
                .build();

        assertThat(chatRequest.temperature()).isEqualTo(0.7);
    }

    @Test
    void should_override_stopSequences_when_both_parameters_and_stopSequences_are_set() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder()
                        .stopSequences(List.of("STOP"))
                        .build())
                .stopSequences(List.of("END"))
                .build();

        assertThat(chatRequest.stopSequences()).containsExactly("END");
    }

    @Test
    void should_override_toolSpecifications_when_both_parameters_and_toolSpecifications_are_set() {
        ToolSpecification paramTool = ToolSpecification.builder()
                .name("paramTool")
                .description("desc")
                .build();
        ToolSpecification builderTool = ToolSpecification.builder()
                .name("builderTool")
                .description("desc")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(List.of(paramTool))
                        .build())
                .toolSpecifications(builderTool)
                .build();

        assertThat(chatRequest.toolSpecifications()).containsExactly(builderTool);
    }

    @Test
    void should_override_responseFormat_when_both_parameters_and_responseFormat_are_set() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(ResponseFormat.JSON)
                        .build())
                .responseFormat(ResponseFormat.TEXT)
                .build();

        assertThat(chatRequest.responseFormat()).isEqualTo(ResponseFormat.TEXT);
    }

    @Test
    void should_override_responseFormat_via_toBuilder() {
        // given
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("gpt-4")
                        .temperature(0.7)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        // then
        assertThat(modified.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(modified.modelName()).isEqualTo("gpt-4");
        assertThat(modified.temperature()).isEqualTo(0.7);
        assertThat(modified.messages()).containsExactly(UserMessage.from("hi"));
    }

    @Test
    void should_override_existing_responseFormat_via_toBuilder() {
        // given
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(ResponseFormat.TEXT)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        // then
        assertThat(modified.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void should_override_multiple_fields_via_toBuilder() {
        // given
        ToolSpecification tool = ToolSpecification.builder().name("tool").build();
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("gpt-4")
                        .temperature(0.7)
                        .maxOutputTokens(100)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .temperature(0.2)
                .responseFormat(ResponseFormat.JSON)
                .toolSpecifications(tool)
                .build();

        // then
        assertThat(modified.modelName()).isEqualTo("gpt-4");
        assertThat(modified.temperature()).isEqualTo(0.2);
        assertThat(modified.maxOutputTokens()).isEqualTo(100);
        assertThat(modified.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(modified.toolSpecifications()).containsExactly(tool);
    }

    @Test
    void should_not_modify_anything_when_toBuilder_used_without_setters() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName("gpt-4")
                .temperature(0.7)
                .responseFormat(ResponseFormat.JSON)
                .build();
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .build();

        // when
        ChatRequest modified = original.toBuilder().build();

        // then
        assertThat(modified).isEqualTo(original);
        assertThat(modified.parameters()).isSameAs(parameters);
    }

    @Test
    void should_not_clear_existing_responseFormat_when_setter_called_with_null() {
        // given
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(ResponseFormat.JSON)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .responseFormat(null)
                .build();

        // then: overrideWith skips null overrides, so existing value is preserved
        assertThat(modified.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void should_not_clear_existing_toolSpecifications_when_setter_called_with_empty() {
        // given
        ToolSpecification tool = ToolSpecification.builder().name("tool").build();
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(tool)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .toolSpecifications()
                .build();

        // then: overrideWith skips empty list overrides, so existing tools are preserved
        assertThat(modified.toolSpecifications()).containsExactly(tool);
    }

    @Test
    void should_apply_zero_value_via_setter() {
        // given: 0.0 must not be treated as null/default
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .temperature(0.7)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .temperature(0.0)
                .build();

        // then
        assertThat(modified.temperature()).isEqualTo(0.0);
    }

    @Test
    void should_last_setter_call_win_on_individual_field() {
        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .temperature(0.1)
                .temperature(0.2)
                .temperature(0.3)
                .build();

        // then
        assertThat(chatRequest.temperature()).isEqualTo(0.3);
    }

    @Test
    void should_be_independent_of_setter_order_parameters_then_individual() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        // when: .parameters() called before .responseFormat()
        ChatRequest a = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .responseFormat(ResponseFormat.TEXT)
                .build();

        // when: .responseFormat() called before .parameters()
        ChatRequest b = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .responseFormat(ResponseFormat.TEXT)
                .parameters(parameters)
                .build();

        // then
        assertThat(a).isEqualTo(b);
        assertThat(a.responseFormat()).isEqualTo(ResponseFormat.TEXT);
    }

    @Test
    void should_fully_replace_parameters_when_parameters_called_again() {
        // given
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("gpt-4")
                        .temperature(0.7)
                        .build())
                .build();

        ChatRequestParameters replacement = ChatRequestParameters.builder()
                .maxOutputTokens(50)
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .parameters(replacement)
                .build();

        // then: original modelName/temperature are gone, only replacement fields remain
        assertThat(modified.modelName()).isNull();
        assertThat(modified.temperature()).isNull();
        assertThat(modified.maxOutputTokens()).isEqualTo(50);
    }

    @Test
    void should_clear_parameters_when_parameters_called_with_null() {
        // given
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("gpt-4")
                        .temperature(0.7)
                        .responseFormat(ResponseFormat.JSON)
                        .build())
                .build();

        // when
        ChatRequest modified = original.toBuilder()
                .parameters(null)
                .build();

        // then: all parameters cleared (result has empty default parameters)
        assertThat(modified.modelName()).isNull();
        assertThat(modified.temperature()).isNull();
        assertThat(modified.responseFormat()).isNull();
    }

    @Test
    void should_preserve_provider_specific_subclass_fields_via_toBuilder() {
        // given: provider-specific subclass with an extra field
        TestProviderChatRequestParameters parameters = TestProviderChatRequestParameters.builder()
                .modelName("test-model")
                .temperature(0.5)
                .customField("custom-value")
                .build();

        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .build();

        // when: override a common field via toBuilder
        ChatRequest modified = original.toBuilder()
                .temperature(0.9)
                .build();

        // then: provider-specific field is preserved, base field is overridden
        assertThat(modified.parameters()).isInstanceOf(TestProviderChatRequestParameters.class);
        TestProviderChatRequestParameters modifiedParams =
                (TestProviderChatRequestParameters) modified.parameters();
        assertThat(modifiedParams.customField()).isEqualTo("custom-value");
        assertThat(modifiedParams.modelName()).isEqualTo("test-model");
        assertThat(modifiedParams.temperature()).isEqualTo(0.9);
    }

    @Test
    void should_preserve_provider_specific_subclass_type_when_toBuilder_used_without_setters() {
        // given
        TestProviderChatRequestParameters parameters = TestProviderChatRequestParameters.builder()
                .customField("custom-value")
                .build();

        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .build();

        // when
        ChatRequest modified = original.toBuilder().build();

        // then: original parameters object passed through as-is (identity preserved,
        // so subclasses that do not override overrideWith still keep their type)
        assertThat(modified.parameters()).isSameAs(parameters);
    }

    public static class TestProviderChatRequestParameters extends DefaultChatRequestParameters {

        private final String customField;

        private TestProviderChatRequestParameters(Builder builder) {
            super(builder);
            this.customField = builder.customField;
        }

        public String customField() {
            return customField;
        }

        @Override
        public TestProviderChatRequestParameters overrideWith(ChatRequestParameters that) {
            return TestProviderChatRequestParameters.builder()
                    .overrideWith(this)
                    .overrideWith(that)
                    .build();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            TestProviderChatRequestParameters that = (TestProviderChatRequestParameters) o;
            return Objects.equals(customField, that.customField);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), customField);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

            private String customField;

            @Override
            public Builder overrideWith(ChatRequestParameters parameters) {
                super.overrideWith(parameters);
                if (parameters instanceof TestProviderChatRequestParameters testParameters) {
                    customField(getOrDefault(testParameters.customField(), customField));
                }
                return this;
            }

            public Builder customField(String customField) {
                this.customField = customField;
                return this;
            }

            @Override
            public TestProviderChatRequestParameters build() {
                return new TestProviderChatRequestParameters(this);
            }
        }
    }
}
