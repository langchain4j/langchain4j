package dev.langchain4j.chain;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationalChainTest {

    private final String aiMessage = "Hi there";

    private final String question = "Hello";

    private final ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);

    private final EmbeddingStoreRetriever retriever = mock(EmbeddingStoreRetriever.class);

    private final ChatMemory chatMemory = spy(MessageWindowChatMemory.withMaxMessages(10));

    private final PromptTemplate promptTemplate = mock(PromptTemplate.class);

    @Captor
    ArgumentCaptor<Map<String, Object>> variablesCaptor;

    @Test
    void should_not_include_metadata_keys_in_chat_memory_when_metadata_null() {
        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from("Segment 1", Metadata.metadata("title", "title_1.pdf")));
        segments.add(TextSegment.from("Segment 2", Metadata.metadata("title", "title_2.pdf")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .promptTemplate(promptTemplate)
                .chatMemory(chatMemory)
                .retriever(retriever)
                .build();

        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(aiMessage(aiMessage)));
        when(retriever.findRelevant(question)).thenReturn(segments);
        when(promptTemplate.apply(any())).thenReturn(Prompt.from("Generated prompt"));

        String result = chain.execute(question);

        verify(promptTemplate).apply(variablesCaptor.capture());
        verify(chatMemory, times(2)).add(any());
        verify(chatLanguageModel).generate(anyList());

        Map<String, Object> capturedVariables = variablesCaptor.getValue();
        String capturedInformation = (String) capturedVariables.get("information");

        Assertions.assertEquals("Hi there", result);
        Assertions.assertEquals("...Segment 1...\n\n...Segment 2...", capturedInformation);
    }

    @Test
    void should_store_user_and_ai_messages_in_chat_memory() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(aiMessage(aiMessage)));

        ChatMemory chatMemory = spy(MessageWindowChatMemory.withMaxMessages(10));

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When
        String response = chain.execute(question);

        // Then
        assertThat(response).isEqualTo(aiMessage);

        verify(chatMemory).add(userMessage(question));
        verify(chatMemory, times(3)).messages();
        verify(chatLanguageModel).generate(singletonList(userMessage(question)));
        verify(chatMemory).add(aiMessage(aiMessage));

        verifyNoMoreInteractions(chatMemory);
        verifyNoMoreInteractions(chatLanguageModel);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void should_fail_when_user_message_is_null_or_blank(String userMessage) {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = mock(ChatMemory.class);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When & Then
        assertThatThrownBy(() -> chain.execute(userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage cannot be null or blank");
    }

}
