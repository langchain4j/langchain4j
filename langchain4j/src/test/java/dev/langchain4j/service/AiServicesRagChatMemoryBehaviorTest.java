package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import java.util.List;
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

    @Test
    void should_store_augmented_message_in_memory_by_default() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("answer"))
                        .build());

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
        assertThat(((UserMessage) messages.get(0))).isEqualTo(UserMessage.from("hello [augmented]"));
    }

    @Test
    void should_store_only_original_message_in_memory_when_disabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("answer"))
                        .build());

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
        assertThat(((UserMessage) messages.get(0))).isEqualTo(UserMessage.from("hello"));
    }
}
