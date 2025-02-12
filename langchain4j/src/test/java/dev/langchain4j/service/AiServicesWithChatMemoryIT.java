package dev.langchain4j.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.AiServicesWithChatMemoryIT.ChatWithMemory.ANOTHER_SYSTEM_MESSAGE;
import static dev.langchain4j.service.AiServicesWithChatMemoryIT.ChatWithMemory.SYSTEM_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesWithChatMemoryIT {

    @Spy
    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    interface ChatWithMemory {

        String SYSTEM_MESSAGE = "You are helpful assistant";
        String ANOTHER_SYSTEM_MESSAGE = "You are funny assistant";

        String chat(String userMessage);

        @SystemMessage(SYSTEM_MESSAGE)
        String chatWithSystemMessage(String userMessage);

        @SystemMessage(ANOTHER_SYSTEM_MESSAGE)
        String chatWithAnotherSystemMessage(String userMessage);
    }

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatLanguageModel);
        verifyNoMoreInteractions(chatMemory);
    }

    @Test
    void should_keep_chat_memory() {

        // given
        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";

        // when
        String firstAiMessage = chatWithMemory.chat(firstUserMessage);

        // then
        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatLanguageModel).chat(chatRequest(firstUserMessage));
        verify(chatMemory).add(aiMessage(firstAiMessage));


        // given
        String secondUserMessage = "What is my name?";

        // when
        String secondAiMessage = chatWithMemory.chat(secondUserMessage);

        // then
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        userMessage(firstUserMessage),
                        aiMessage(firstAiMessage),
                        userMessage(secondUserMessage)
                ).build()
        );
        verify(chatMemory).add(aiMessage(secondAiMessage));


        // given
        String thirdUserMessage = "I am 42 years old";

        // when
        String thirdAiMessage = chatWithMemory.chat(thirdUserMessage);

        // then
        verify(chatMemory).add(userMessage(thirdUserMessage));
        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        userMessage(firstUserMessage),
                        aiMessage(firstAiMessage),
                        userMessage(secondUserMessage),
                        aiMessage(secondAiMessage),
                        userMessage(thirdUserMessage)
                ).build()
        );
        verify(chatMemory).add(aiMessage(thirdAiMessage));


        // given
        String fourthUserMessage = "How old am I?";

        // when
        String fourthAiMessage = chatWithMemory.chat(fourthUserMessage);

        // then
        assertThat(fourthAiMessage).contains("42");

        verify(chatMemory).add(userMessage(fourthUserMessage));
        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        userMessage(firstUserMessage),
                        aiMessage(firstAiMessage),
                        userMessage(secondUserMessage),
                        aiMessage(secondAiMessage),
                        userMessage(thirdUserMessage),
                        aiMessage(thirdAiMessage),
                        userMessage(fourthUserMessage)
                ).build()
        );
        verify(chatMemory).add(aiMessage(fourthAiMessage));

        verify(chatLanguageModel, times(4)).supportedCapabilities();
        verify(chatMemory, times(12)).messages();
    }

    @Test
    void should_keep_chat_memory_and_not_duplicate_system_message() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage(SYSTEM_MESSAGE),
                        userMessage(firstUserMessage)
                ).build()
        );

        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage(SYSTEM_MESSAGE),
                        userMessage(firstUserMessage),
                        aiMessage(firstAiMessage),
                        userMessage(secondUserMessage)
                ).build()
        );
        verify(chatLanguageModel, times(2)).supportedCapabilities();

        verify(chatMemory, times(2)).add(systemMessage(SYSTEM_MESSAGE));
        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatMemory).add(aiMessage(firstAiMessage));
        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory, times(8)).messages();
    }

    @Test
    void should_keep_chat_memory_and_add_new_system_message() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        systemMessage(SYSTEM_MESSAGE),
                        userMessage(firstUserMessage)
                ).build()
        );


        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithAnotherSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        userMessage(firstUserMessage),
                        aiMessage(firstAiMessage),
                        systemMessage(ANOTHER_SYSTEM_MESSAGE),
                        userMessage(secondUserMessage)
                ).build()
        );
        verify(chatLanguageModel, times(2)).supportedCapabilities();

        verify(chatMemory).add(systemMessage(SYSTEM_MESSAGE));
        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatMemory).add(aiMessage(firstAiMessage));
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory).add(systemMessage(ANOTHER_SYSTEM_MESSAGE));
        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatMemory, times(8)).messages();
    }


    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() {

        // emulating persistent storage
        Map</* memoryId */ Object, String> persistentStorage = new HashMap<>();

        ChatMemoryStore store = new ChatMemoryStore() {

            @Override
            public List<ChatMessage> getMessages(Object memoryId) {
                return messagesFromJson(persistentStorage.get(memoryId));
            }

            @Override
            public void updateMessages(Object memoryId, List<ChatMessage> messages) {
                persistentStorage.put(memoryId, messagesToJson(messages));
            }

            @Override
            public void deleteMessages(Object memoryId) {
                persistentStorage.remove(memoryId);
            }
        };

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();

        int firstMemoryId = 1;
        int secondMemoryId = 2;

        ChatWithSeparateMemoryForEachUser chatWithMemory = AiServices.builder(ChatWithSeparateMemoryForEachUser.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();

        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        String firstAiResponseToFirstUser = chatWithMemory.chat(firstMemoryId, firstMessageFromFirstUser);
        verify(chatLanguageModel).chat(chatRequest(firstMessageFromFirstUser));

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        String firstAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, firstMessageFromSecondUser);
        verify(chatLanguageModel).chat(chatRequest(firstMessageFromSecondUser));

        String secondMessageFromFirstUser = "What is my name?";
        String secondAiResponseToFirstUser = chatWithMemory.chat(firstMemoryId, secondMessageFromFirstUser);
        assertThat(secondAiResponseToFirstUser).contains("Klaus");
        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        userMessage(firstMessageFromFirstUser),
                        aiMessage(firstAiResponseToFirstUser),
                        userMessage(secondMessageFromFirstUser)
                ).build()
        );

        String secondMessageFromSecondUser = "What is my name?";
        String secondAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, secondMessageFromSecondUser);
        assertThat(secondAiResponseToSecondUser).contains("Francine");
        verify(chatLanguageModel).chat(ChatRequest.builder()
                .messages(
                        userMessage(firstMessageFromSecondUser),
                        aiMessage(firstAiResponseToSecondUser),
                        userMessage(secondMessageFromSecondUser)
                ).build()
        );

        assertThat(persistentStorage).containsOnlyKeys(firstMemoryId, secondMemoryId);

        List<ChatMessage> persistedMessagesOfFirstUser = messagesFromJson(persistentStorage.get(firstMemoryId));
        assertThat(persistedMessagesOfFirstUser).containsExactly(
                userMessage(firstMessageFromFirstUser),
                aiMessage(firstAiResponseToFirstUser),
                userMessage(secondMessageFromFirstUser),
                aiMessage(secondAiResponseToFirstUser)
        );

        List<ChatMessage> persistedMessagesOfSecondUser = messagesFromJson(persistentStorage.get(secondMemoryId));
        assertThat(persistedMessagesOfSecondUser).containsExactly(
                userMessage(firstMessageFromSecondUser),
                aiMessage(firstAiResponseToSecondUser),
                userMessage(secondMessageFromSecondUser),
                aiMessage(secondAiResponseToSecondUser)
        );

        verify(chatLanguageModel, times(4)).supportedCapabilities();
    }
}
