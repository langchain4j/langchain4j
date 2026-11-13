package dev.langchain4j.model.chat.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class ChatModelMockTest {

    private static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("1")
            .name("getWeather")
            .arguments("{}")
            .build());

    @Test
    void should_respond_with_a_single_response_to_every_request() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("Hi"));

        assertThat(chat(model).text()).isEqualTo("Hi");
        assertThat(chat(model).text()).isEqualTo("Hi");
    }

    @Test
    void should_respond_in_order_then_repeat_the_last_response() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(AiMessage.from("first"), AiMessage.from("second"));

        assertThat(chat(model).text()).isEqualTo("first");
        assertThat(chat(model).text()).isEqualTo("second");
        assertThat(chat(model).text()).isEqualTo("second");
    }

    @Test
    void should_fail_instead_of_repeating_a_response_with_tool_calls() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(TOOL_CALL);

        assertThat(chat(model).hasToolExecutionRequests()).isTrue();
        assertThatThrownBy(() -> chat(model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ChatModelMock has no more responses: the last scripted response (with tool calls) would"
                        + " be repeated, which would loop forever. Script the response that follows the tool"
                        + " results.");
    }

    @Test
    void should_report_the_given_token_usage_and_finish_reason_in_every_response() {

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Hi")
                .withTokenUsage(new TokenUsage(1, 2))
                .withFinishReason(FinishReason.LENGTH);

        for (int i = 0; i < 2; i++) {
            ChatResponse response = chatResponse(model);
            assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(1, 2));
            assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        }
    }

    @Test
    void should_report_the_given_token_usages_in_order_then_repeat_the_last_one() {

        ChatModelMock model =
                ChatModelMock.thatAlwaysResponds("Hi").withTokenUsages(new TokenUsage(1, 2), new TokenUsage(3, 4));

        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(1, 2));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
        assertThat(chatResponse(model).tokenUsage()).isEqualTo(new TokenUsage(3, 4));
    }

    @Test
    void should_report_no_token_usage_and_finish_reason_by_default() {

        ChatResponse response = chatResponse(ChatModelMock.thatAlwaysResponds("Hi"));

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isNull();
    }

    private static AiMessage chat(ChatModelMock model) {
        return chatResponse(model).aiMessage();
    }

    private static ChatResponse chatResponse(ChatModelMock model) {
        return model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }
}
