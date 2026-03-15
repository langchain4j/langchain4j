package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies how RAG-augmented user messages are stored in chat memory depending on
 * {@link AiServices#storeRetrievedContentInChatMemory(boolean)} configuration.
 */
class AiServicesRagChatMemoryBehaviorTest {

    interface Assistant {

        @dev.langchain4j.service.UserMessage("{{it}}")
        String chat(String question);
    }

    interface StreamingAssistant {

        @dev.langchain4j.service.UserMessage("{{it}}")
        TokenStream chat(String question);
    }

    @Test
    void should_store_augmented_message_in_memory_by_default() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("answer");

        RetrievalAugmentor retrievalAugmentor = (AugmentationRequest request) -> {
            UserMessage original = (UserMessage) request.chatMessage();
            UserMessage augmented = UserMessage.from(original.singleText() + " [augmented]");
            return new AugmentationResult(augmented, null);
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(chatMemory)
                .build();

        assistant.chat("hello");

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(2);

        List<List<ChatMessage>> requests = chatModel.getRequests();
        assertThat(requests).hasSize(1);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) messages.get(0)).isEqualTo(UserMessage.from("hello [augmented]"));
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat((AiMessage) messages.get(1)).isEqualTo(AiMessage.from("answer"));

        List<ChatMessage> llmMessages1 = requests.get(0);
        ChatMessage last1 = llmMessages1.get(llmMessages1.size() - 1);
        assertThat(last1).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) last1).isEqualTo(UserMessage.from("hello [augmented]"));

        assistant.chat("hi again");

        messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        requests = chatModel.getRequests();
        assertThat(requests).hasSize(2);

        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) messages.get(2)).isEqualTo(UserMessage.from("hi again [augmented]"));
        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat((AiMessage) messages.get(3)).isEqualTo(AiMessage.from("answer"));

        List<ChatMessage> llmMessages2 = requests.get(1);
        ChatMessage last2 = llmMessages2.get(llmMessages2.size() - 1);
        assertThat(last2).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) last2).isEqualTo(UserMessage.from("hi again [augmented]"));
    }

    @Test
    void should_store_only_original_message_in_memory_when_disabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("answer");

        RetrievalAugmentor retrievalAugmentor = (AugmentationRequest request) -> {
            UserMessage original = (UserMessage) request.chatMessage();
            UserMessage augmented = UserMessage.from(original.singleText() + " [augmented]");
            return new AugmentationResult(augmented, null);
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemory(chatMemory)
                .storeRetrievedContentInChatMemory(false)
                .build();

        assistant.chat("hello");

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(2);

        List<List<ChatMessage>> requests = chatModel.getRequests();
        assertThat(requests).hasSize(1);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) messages.get(0)).isEqualTo(UserMessage.from("hello"));
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat((AiMessage) messages.get(1)).isEqualTo(AiMessage.from("answer"));

        List<ChatMessage> llmMessages1 = requests.get(0);
        ChatMessage last1 = llmMessages1.get(llmMessages1.size() - 1);
        assertThat(last1).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) last1).isEqualTo(UserMessage.from("hello [augmented]"));

        assistant.chat("hi again");

        messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        requests = chatModel.getRequests();
        assertThat(requests).hasSize(2);

        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) messages.get(2)).isEqualTo(UserMessage.from("hi again"));
        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat((AiMessage) messages.get(3)).isEqualTo(AiMessage.from("answer"));

        List<ChatMessage> llmMessages2 = requests.get(1);
        ChatMessage last2 = llmMessages2.get(llmMessages2.size() - 1);
        assertThat(last2).isInstanceOf(UserMessage.class);
        assertThat((UserMessage) last2).isEqualTo(UserMessage.from("hi again [augmented]"));
    }

    @Test
    void should_replay_augmented_message_on_second_model_call_when_tool_loop_runs_and_storage_is_disabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModelMock chatModel = toolThenAnswerModel("hello");

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentingRetrievalAugmentor())
                .chatMemory(chatMemory)
                .storeRetrievedContentInChatMemory(false)
                .tools(new LookupTool())
                .build();

        assistant.chat("hello");

        assertThat(chatModel.requests()).hasSize(2);
        assertThat(lastUserMessage(chatModel.requests().get(1).messages()))
                .isEqualTo(UserMessage.from("hello [augmented]"));
    }

    @Test
    void should_keep_replaying_augmented_message_on_second_model_call_when_storage_is_enabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModelMock chatModel = toolThenAnswerModel("hello");

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentingRetrievalAugmentor())
                .chatMemory(chatMemory)
                .tools(new LookupTool())
                .build();

        assistant.chat("hello");

        assertThat(chatModel.requests()).hasSize(2);
        assertThat(lastUserMessage(chatModel.requests().get(1).messages()))
                .isEqualTo(UserMessage.from("hello [augmented]"));
    }

    @Test
    void should_keep_original_message_in_memory_after_tool_loop_when_storage_is_disabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModelMock chatModel = toolThenAnswerModel("hello");

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentingRetrievalAugmentor())
                .chatMemory(chatMemory)
                .storeRetrievedContentInChatMemory(false)
                .tools(new LookupTool())
                .build();

        assistant.chat("hello");

        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isEqualTo(UserMessage.from("hello"));
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(((ToolExecutionResultMessage) messages.get(2)).text()).isEqualTo("lookup: hello");
        assertThat(messages.get(3)).isEqualTo(AiMessage.from("answer"));
    }

    @Test
    void should_replay_augmented_message_across_two_sequential_tool_calls_when_storage_is_disabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        ToolExecutionRequest tool1 = ToolExecutionRequest.builder()
                .name("lookup")
                .arguments("{\"arg0\":\"hello\"}")
                .build();
        ToolExecutionRequest tool2 = ToolExecutionRequest.builder()
                .name("lookup")
                .arguments("{\"arg0\":\"world\"}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(tool1), AiMessage.from(tool2), AiMessage.from("answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentingRetrievalAugmentor())
                .chatMemory(chatMemory)
                .storeRetrievedContentInChatMemory(false)
                .tools(new LookupTool())
                .build();

        assistant.chat("hello");

        assertThat(chatModel.requests()).hasSize(3);
        assertThat(lastUserMessage(chatModel.requests().get(1).messages()))
                .isEqualTo(UserMessage.from("hello [augmented]"));
        assertThat(lastUserMessage(chatModel.requests().get(2).messages()))
                .isEqualTo(UserMessage.from("hello [augmented]"));
    }

    @Test
    void should_replay_augmented_message_in_streaming_when_tool_loop_runs_and_storage_is_disabled() throws Exception {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("lookup")
                .arguments("{\"arg0\":\"hello\"}")
                .build();
        StreamingChatModelMock delegate = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest), AiMessage.from("answer"));

        List<ChatRequest> capturedRequests = new ArrayList<>();
        StreamingChatModel recordingModel = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                capturedRequests.add(chatRequest);
                delegate.doChat(chatRequest, handler);
            }
        };

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(recordingModel)
                .retrievalAugmentor(augmentingRetrievalAugmentor())
                .chatMemory(chatMemory)
                .storeRetrievedContentInChatMemory(false)
                .tools(new LookupTool())
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        assistant
                .chat("hello")
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        futureResponse.get(30, TimeUnit.SECONDS);

        assertThat(capturedRequests).hasSize(2);
        assertThat(lastUserMessage(capturedRequests.get(1).messages()))
                .isEqualTo(UserMessage.from("hello [augmented]"));
    }

    private static RetrievalAugmentor augmentingRetrievalAugmentor() {
        return (AugmentationRequest request) -> {
            UserMessage original = (UserMessage) request.chatMessage();
            UserMessage augmented = UserMessage.from(original.singleText() + " [augmented]");
            return new AugmentationResult(augmented, null);
        };
    }

    private static ChatModelMock toolThenAnswerModel(String question) {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("lookup")
                .arguments("{\"arg0\":\"" + question + "\"}")
                .build();
        return ChatModelMock.thatAlwaysResponds(AiMessage.from(toolExecutionRequest), AiMessage.from("answer"));
    }

    private static UserMessage lastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage;
            }
        }
        throw new AssertionError("Expected at least one UserMessage");
    }

    static class LookupTool {

        @Tool
        String lookup(String question) {
            return "lookup: " + question;
        }
    }
}
