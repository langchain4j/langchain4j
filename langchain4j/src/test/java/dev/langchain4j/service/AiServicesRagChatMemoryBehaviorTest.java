package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
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
}
