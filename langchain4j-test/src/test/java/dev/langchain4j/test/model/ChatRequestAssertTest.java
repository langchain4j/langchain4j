package dev.langchain4j.test.model;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ChatRequestAssertTest {

    interface Assistant {

        @dev.langchain4j.service.SystemMessage("You are the support agent of a car rental company.")
        String chat(String message);
    }

    static class BookingTools {

        @Tool
        String cancelBooking(@P(name = "bookingNumber", value = "the booking number") String bookingNumber) {
            return "Booking " + bookingNumber + " cancelled";
        }
    }

    static final ChatRequest REQUEST = ChatRequest.builder()
            .messages(
                    SystemMessage.from("You are a support agent."),
                    UserMessage.from("Hi"),
                    AiMessage.from("Hello! How can I help?"),
                    UserMessage.from(
                            TextContent.from("Cancel MS-777"), ImageContent.from("https://example.com/booking.png")),
                    AiMessage.from(ToolExecutionRequest.builder()
                            .id("1")
                            .name("cancelBooking")
                            .arguments("{\"bookingNumber\":\"MS-777\"}")
                            .build()),
                    ToolExecutionResultMessage.from("1", "cancelBooking", "Booking MS-777 cancelled"))
            .toolSpecifications(ToolSpecification.builder().name("cancelBooking").build())
            .build();

    @Test
    void should_pass_when_the_request_has_the_messages_and_tools() {
        assertThat(REQUEST)
                .hasSystemMessageContaining("support agent")
                .hasUserMessageContaining("Hi")
                .hasUserMessageContaining("MS-777")
                .hasToolSpecification("cancelBooking");
    }

    @Test
    void should_fail_with_all_messages_when_no_system_message_contains_the_text() {
        assertThatThrownBy(() -> assertThat(REQUEST).hasSystemMessageContaining("travel agent"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the request to have a system message containing:")
                .hasMessageContaining("travel agent")
                .hasMessageContaining("system: You are a support agent.")
                .hasMessageContaining("user: Cancel MS-777\n  [image 1]")
                .hasMessageContaining("assistant: [calls tool cancelBooking (id 1) with {\"bookingNumber\":\"MS-777\"}]")
                .hasMessageContaining("tool: [result of tool cancelBooking (id 1)] Booking MS-777 cancelled");
    }

    @Test
    void should_check_only_user_messages() {
        assertThatThrownBy(() -> assertThat(REQUEST).hasUserMessageContaining("How can I help"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the request to have a user message containing:")
                .hasMessageContaining("assistant: Hello! How can I help?");
    }

    @Test
    void should_cut_long_messages_in_failure() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("m".repeat(1000) + "n".repeat(30)))
                .build();

        assertThatThrownBy(() -> assertThat(request).hasSystemMessageContaining("support agent"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("m".repeat(1000) + "...(30 more characters)")
                .hasMessageNotContaining("n".repeat(10));
    }

    @Test
    void should_fail_with_the_offered_tools() {
        assertThatThrownBy(() -> assertThat(REQUEST).hasToolSpecification("getBookingDetails"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Expected the request to offer the tool getBookingDetails, but it offered [cancelBooking]");

        ChatRequest withoutTools =
                ChatRequest.builder().messages(UserMessage.from("Hi")).build();
        assertThatThrownBy(() -> assertThat(withoutTools).hasToolSpecification("cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Expected the request to offer the tool cancelBooking, but it offered no tools");
    }

    @Test
    void should_fail_for_a_null_request() {
        ChatRequest request = null;

        assertThatThrownBy(() -> assertThat(request).hasUserMessageContaining("Hi"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expecting actual not to be null");
    }

    @Test
    void should_check_only_the_last_user_message() {
        assertThat(REQUEST).hasLastUserMessageContaining("MS-777");

        assertThatThrownBy(() -> assertThat(REQUEST).hasLastUserMessageContaining("Hi"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the last user message of the request to contain:")
                .hasMessageContaining("user: Hi");

        ChatRequest withoutUserMessage = ChatRequest.builder()
                .messages(SystemMessage.from("You are a support agent."))
                .build();
        assertThatThrownBy(() -> assertThat(withoutUserMessage).hasLastUserMessageContaining("Hi"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the last user message of the request to contain:");
    }

    @Test
    void should_reject_null_and_blank_arguments() {
        assertThatThrownBy(() -> assertThat(REQUEST).hasUserMessageContaining(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> assertThat(REQUEST).hasLastUserMessageContaining(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> assertThat(REQUEST).hasToolSpecification(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
    }

    @Test
    void should_check_the_requests_of_a_chat_model_mock() {
        ChatModelMock model = ChatModelMock.thatResponds(request -> request.messages()
                        .get(request.messages().size() - 1)
                        instanceof ToolExecutionResultMessage
                ? AiMessage.from("Booking MS-777 is cancelled.")
                : AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("cancelBooking")
                        .arguments("{\"bookingNumber\":\"MS-777\"}")
                        .build()));
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new BookingTools())
                .build();

        assistant.chat("Cancel MS-777");

        assertThat(model.requests().get(0))
                .hasSystemMessageContaining("car rental")
                .hasUserMessageContaining("MS-777")
                .hasToolSpecification("cancelBooking");
        assertThat(model.requests().get(1)).hasUserMessageContaining("MS-777");

        ChatModelMock singleRequestModel = ChatModelMock.thatAlwaysResponds("Hello");
        singleRequestModel.chat(REQUEST);
        assertThat(singleRequestModel.request()).hasSystemMessageContaining("support agent");
    }

    @Test
    void should_check_the_requests_collected_by_a_chat_model_listener() {
        // the same code as in the Javadoc of ChatRequestAssert
        List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        ChatModelListener requestCollector = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                requests.add(requestContext.chatRequest());
            }
        };
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Your booking is cancelled.")
                        .withListeners(requestCollector))
                .tools(new BookingTools())
                .build();

        assistant.chat("Cancel booking MS-777");

        assertThat(requests.get(0))
                .hasSystemMessageContaining("car rental")
                .hasUserMessageContaining("MS-777")
                .hasToolSpecification("cancelBooking");
    }
}
