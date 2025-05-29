package dev.langchain4j.chain;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationalRetrievalChainTest {

    private static final String QUERY = "query";
    private static final String ANSWER = "answer";

    @Mock
    ChatModel chatModel;

    @Mock
    ContentRetriever contentRetriever;

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    @Captor
    ArgumentCaptor<List<ChatMessage>> messagesCaptor;

    @BeforeEach
    void beforeEach() {
        when(chatModel.chat(anyList())).thenReturn(ChatResponse.builder().aiMessage(aiMessage(ANSWER)).build());
    }

    @Test
    void should_inject_retrieved_segments() {

        // given
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Segment 1"), Content.from("Segment 2")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from(
                "query\n" + "\n" + "Answer using the following information:\n" + "Segment 1\n" + "\n" + "Segment 2");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(expectedUserMessage, AiMessage.from(ANSWER));
    }

    @Test
    void should_inject_retrieved_segments_using_custom_prompt_template() {

        // given
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Segment 1"), Content.from("Segment 2")));

        PromptTemplate promptTemplate = PromptTemplate.from("Answer '{{userMessage}}' using '{{contents}}'");

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(promptTemplate)
                                .build())
                        .build())
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from("Answer 'query' using 'Segment 1\n\nSegment 2'");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(expectedUserMessage, AiMessage.from(ANSWER));
    }
}
