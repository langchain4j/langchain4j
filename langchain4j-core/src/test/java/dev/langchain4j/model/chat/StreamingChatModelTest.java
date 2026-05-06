package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ServerToolExecution;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class StreamingChatModelTest implements WithAssertions {

    public static class StreamingUpperCaseEchoModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            UserMessage lastMessage = (UserMessage) messages.get(messages.size() - 1);
            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(new AiMessage(lastMessage.singleText().toUpperCase(Locale.ROOT)))
                    .build();
            handler.onCompleteResponse(chatResponse);
        }
    }

    public static final class CollectorResponseHandler implements StreamingChatResponseHandler {

        private final List<ChatResponse> responses = new ArrayList<>();
        private final List<ServerToolExecution> beforeServerToolExecutions = new ArrayList<>();
        private final List<ServerToolExecution> serverToolExecutionProgressEvents = new ArrayList<>();
        private final List<ServerToolExecution> serverToolExecutedEvents = new ArrayList<>();

        public List<ChatResponse> responses() {
            return responses;
        }

        public List<ServerToolExecution> beforeServerToolExecutions() {
            return beforeServerToolExecutions;
        }

        public List<ServerToolExecution> serverToolExecutionProgressEvents() {
            return serverToolExecutionProgressEvents;
        }

        public List<ServerToolExecution> serverToolExecutedEvents() {
            return serverToolExecutedEvents;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
        }

        @Override
        public void beforeServerToolExecution(ServerToolExecution serverToolExecution) {
            beforeServerToolExecutions.add(serverToolExecution);
        }

        @Override
        public void onServerToolExecutionProgress(ServerToolExecution serverToolExecution) {
            serverToolExecutionProgressEvents.add(serverToolExecution);
        }

        @Override
        public void onServerToolExecuted(ServerToolExecution serverToolExecution) {
            serverToolExecutedEvents.add(serverToolExecution);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            responses.add(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    @Test
    void generate() {
        StreamingChatModel model = new StreamingUpperCaseEchoModel();

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            CollectorResponseHandler handler = new CollectorResponseHandler();
            model.chat(messages, handler);

            ChatResponse response = handler.responses().get(0);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            CollectorResponseHandler handler = new CollectorResponseHandler();
            model.chat("How are you?", handler);

            ChatResponse response = handler.responses().get(0);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }

    @Test
    void should_forward_server_tool_execution_callbacks() {
        ServerToolExecution started = ServerToolExecution.builder()
                .id("tool_1")
                .type("provider.tool.started")
                .build();
        ServerToolExecution progress = ServerToolExecution.builder()
                .id("tool_1")
                .type("provider.tool.progress")
                .build();
        ServerToolExecution completed = ServerToolExecution.builder()
                .id("tool_1")
                .type("provider.tool.completed")
                .build();

        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.beforeServerToolExecution(started);
                handler.onServerToolExecutionProgress(progress);
                handler.onServerToolExecuted(completed);
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(new AiMessage("done"))
                        .build());
            }
        };

        CollectorResponseHandler handler = new CollectorResponseHandler();
        model.chat("search", handler);

        assertThat(handler.beforeServerToolExecutions()).containsExactly(started);
        assertThat(handler.serverToolExecutionProgressEvents()).containsExactly(progress);
        assertThat(handler.serverToolExecutedEvents()).containsExactly(completed);
    }
}
