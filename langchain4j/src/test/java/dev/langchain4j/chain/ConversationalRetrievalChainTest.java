package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.retriever.Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationalRetrievalChainTest {

    private static final String QUERY = "query";
    private static final String ANSWER = "answer";

    @Mock
    ChatLanguageModel chatLanguageModel;

    @Mock
    ContentRetriever contentRetriever;

    @Mock
    Retriever<TextSegment> retriever;

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    @Captor
    ArgumentCaptor<List<ChatMessage>> messagesCaptor;

    @BeforeEach
    void beforeEach() {
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(aiMessage(ANSWER)));
    }

    @Test
    void should_inject_retrieved_segments() {

        // given
        when(contentRetriever.retrieve(any())).thenReturn(asList(
                Content.from("Segment 1"),
                Content.from("Segment 2")
        ));

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .contentRetriever(contentRetriever)
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatLanguageModel).generate(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from(
                "query\n" +
                        "\n" +
                        "Answer using the following information:\n" +
                        "Segment 1\n" +
                        "\n" +
                        "Segment 2");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(
                expectedUserMessage,
                AiMessage.from(ANSWER)
        );
    }

    @Test
    void should_inject_retrieved_segments_using_custom_prompt_template() {

        // given
        when(contentRetriever.retrieve(any())).thenReturn(asList(
                Content.from("Segment 1"),
                Content.from("Segment 2")
        ));

        PromptTemplate promptTemplate = PromptTemplate.from(
                "Answer '{{userMessage}}' using '{{contents}}'");

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatLanguageModel(chatLanguageModel)
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

        verify(chatLanguageModel).generate(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from(
                "Answer 'query' using 'Segment 1\n\nSegment 2'");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(
                expectedUserMessage,
                AiMessage.from(ANSWER)
        );
    }

    @Test
    void test_backward_compatibility_should_inject_retrieved_segments() {

        // given
        when(retriever.findRelevant(QUERY)).thenReturn(asList(
                TextSegment.from("Segment 1"),
                TextSegment.from("Segment 2")
        ));
        when(retriever.toContentRetriever()).thenCallRealMethod();

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .retriever(retriever)
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatLanguageModel).generate(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from(
                "Answer the following question to the best of your ability: query\n" +
                        "\n" +
                        "Base your answer on the following information:\n" +
                        "Segment 1\n" +
                        "\n" +
                        "Segment 2");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(
                expectedUserMessage,
                AiMessage.from(ANSWER)
        );
    }

    @Test
    void test_backward_compatibility_should_inject_retrieved_segments_using_custom_prompt_template() {

        // given
        when(retriever.findRelevant(QUERY)).thenReturn(asList(
                TextSegment.from("Segment 1"),
                TextSegment.from("Segment 2")
        ));
        when(retriever.toContentRetriever()).thenCallRealMethod();

        ConversationalRetrievalChain chain = ConversationalRetrievalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .promptTemplate(PromptTemplate.from("Answer '{{question}}' using '{{information}}'"))
                .retriever(retriever)
                .build();

        // when
        String answer = chain.execute(QUERY);

        // then
        assertThat(answer).isEqualTo(ANSWER);

        verify(chatLanguageModel).generate(messagesCaptor.capture());
        UserMessage expectedUserMessage = UserMessage.from(
                "Answer 'query' using 'Segment 1\n\nSegment 2'");
        assertThat(messagesCaptor.getValue()).containsExactly(expectedUserMessage);

        assertThat(chatMemory.messages()).containsExactly(
                expectedUserMessage,
                AiMessage.from(ANSWER)
        );
    }
}
