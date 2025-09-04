package dev.langchain4j.chain;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
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
        when(chatModel.chat(anyList()))
                .thenReturn(ChatResponse.builder().aiMessage(aiMessage(ANSWER)).build());
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

    @Test
    void should_handle_empty_retrieved_content() {
        when(contentRetriever.retrieve(any())).thenReturn(emptyList());

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        String answer = chain.execute(QUERY);

        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from("query");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(expectedUserMessage, AiMessage.from(ANSWER));
    }

    @Test
    void should_preserve_conversation_history_across_multiple_queries() {
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Info 1")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        chain.execute("First query");
        chain.execute("Second query");
        chain.execute("Third query");

        assertThat(chatMemory.messages()).hasSize(6);
        verify(contentRetriever, times(3)).retrieve(any(Query.class));
        verify(chatModel, times(3)).chat(anyList());
    }

    @Test
    void should_build_without_chat_memory() {
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .build();

        String answer = chain.execute(QUERY);

        assertThat(answer).isEqualTo(ANSWER);
    }

    @Test
    void should_build_with_retrieval_augmentor_only() {
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();

        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .retrievalAugmentor(augmentor)
                .build();

        String answer = chain.execute(QUERY);

        assertThat(answer).isEqualTo(ANSWER);
    }

    @Test
    void should_handle_chat_model_exception() {
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));
        when(chatModel.chat(anyList())).thenThrow(new RuntimeException("Model error"));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        assertThatThrownBy(() -> chain.execute(QUERY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Model error");

        assertThat(chatMemory.messages()).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void should_include_system_message_if_present_in_memory() {
        ChatMemory memoryWithSystem = MessageWindowChatMemory.withMaxMessages(10);
        memoryWithSystem.add(SystemMessage.from("You are a helpful assistant"));

        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(memoryWithSystem)
                .contentRetriever(contentRetriever)
                .build();

        String answer = chain.execute(QUERY);

        assertThat(answer).isEqualTo(ANSWER);

        verify(chatModel).chat(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(2);
        assertThat(messagesCaptor.getValue().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messagesCaptor.getValue().get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    void should_handle_special_characters_in_query() {
        String specialQuery = "What about @#$%^&*()_+-={}[]|\\:\";<>?,./~`?";
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        String answer = chain.execute(specialQuery);

        assertThat(answer).isEqualTo(ANSWER);
        verify(contentRetriever).retrieve(argThat(q -> q.text().equals(specialQuery)));
    }

    @Test
    void should_respect_memory_window_size() {
        ChatMemory limitedMemory = MessageWindowChatMemory.withMaxMessages(2);
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(limitedMemory)
                .contentRetriever(contentRetriever)
                .build();

        chain.execute("Query 1");
        chain.execute("Query 2");
        chain.execute("Query 3");

        assertThat(limitedMemory.messages()).hasSize(2);
    }

    @Test
    void should_pass_metadata_through_query() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        when(contentRetriever.retrieve(any())).thenReturn(asList(Content.from("Content")));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        chain.execute("Query with metadata");

        verify(contentRetriever).retrieve(queryCaptor.capture());
        Query capturedQuery = queryCaptor.getValue();
        assertThat(capturedQuery.text()).isEqualTo("Query with metadata");
        assertThat(capturedQuery).isNotNull();
    }
}
