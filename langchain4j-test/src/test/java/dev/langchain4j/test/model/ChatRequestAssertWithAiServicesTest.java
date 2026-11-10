package dev.langchain4j.test.model;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Checks requests sent by AI Services. The other tests of {@link ChatRequestAssert} are in {@link ChatRequestAssertTest},
 * which also runs without the {@code langchain4j} module.
 */
class ChatRequestAssertWithAiServicesTest {

    interface Assistant {

        @SystemMessage("You are the support agent of a car rental company.")
        String chat(String message);
    }

    static class BookingTools {

        @Tool
        String cancelBooking(@P(name = "bookingNumber", value = "the booking number") String bookingNumber) {
            return "Booking " + bookingNumber + " cancelled";
        }
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
        singleRequestModel.chat(ChatRequestAssertTest.REQUEST);
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
