package dev.langchain4j.rag.content.retriever.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

class HibernateContentRetrieverTest {

    private final HibernateContentRetriever retriever = HibernateContentRetriever.builder()
            .sessionFactory(mock(SessionFactory.class))
            .chatModel(mock(ChatModel.class))
            .databaseStructure("test")
            .build();

    @Test
    void clean_should_strip_content_from_closed_hql_fence() {
        assertThat(retriever.clean("```hql\nSELECT c FROM Customer c\n```")).isEqualTo("\nSELECT c FROM Customer c\n");
    }

    @Test
    void clean_should_strip_content_from_closed_sql_fence() {
        assertThat(retriever.clean("```sql\nSELECT c FROM Customer c\n```")).isEqualTo("\nSELECT c FROM Customer c\n");
    }

    @Test
    void clean_should_strip_content_from_closed_plain_fence() {
        assertThat(retriever.clean("```\nSELECT c FROM Customer c\n```")).isEqualTo("\nSELECT c FROM Customer c\n");
    }

    @Test
    void clean_should_not_throw_when_hql_fence_is_not_closed() {
        assertThatCode(() -> retriever.clean("```hql\nSELECT c FROM Customer c"))
                .doesNotThrowAnyException();
        assertThat(retriever.clean("```hql\nSELECT c FROM Customer c")).isEqualTo("\nSELECT c FROM Customer c");
    }

    @Test
    void clean_should_not_throw_when_sql_fence_is_not_closed() {
        assertThatCode(() -> retriever.clean("```sql\nSELECT c FROM Customer c"))
                .doesNotThrowAnyException();
        assertThat(retriever.clean("```sql\nSELECT c FROM Customer c")).isEqualTo("\nSELECT c FROM Customer c");
    }

    @Test
    void clean_should_not_throw_when_plain_fence_is_not_closed() {
        assertThatCode(() -> retriever.clean("```\nSELECT c FROM Customer c")).doesNotThrowAnyException();
        assertThat(retriever.clean("```\nSELECT c FROM Customer c")).isEqualTo("\nSELECT c FROM Customer c");
    }

    @Test
    void clean_should_return_plain_text_unchanged() {
        assertThat(retriever.clean("SELECT c FROM Customer c")).isEqualTo("SELECT c FROM Customer c");
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = FinishReason.class, names = {"STOP", "OTHER"})
    void should_return_empty_list_when_model_returns_no_text(FinishReason finishReason) {
        SessionFactory sessionFactory = mock(SessionFactory.class);
        MockChatModel chatModel = MockChatModel.thatResponds(emptyAnswer(finishReason));
        HibernateContentRetriever retriever = retriever(sessionFactory, chatModel);

        List<Content> contents = retriever.retrieve(Query.from("hello"));

        assertThat(contents).isEmpty();
        assertThat(chatModel.requests()).hasSize(1);
        verifyNoInteractions(sessionFactory);
    }

    @Test
    void should_fail_when_model_answer_is_cut_off_by_token_limit() {
        SessionFactory sessionFactory = mock(SessionFactory.class);
        MockChatModel chatModel = MockChatModel.thatResponds(emptyAnswer(FinishReason.LENGTH));
        HibernateContentRetriever retriever = retriever(sessionFactory, chatModel);

        assertThatThrownBy(() -> retriever.retrieve(Query.from("How many customers are there?")))
                .isExactlyInstanceOf(LangChain4jException.class)
                .hasMessageContaining("finish reason: LENGTH");
        assertThat(chatModel.requests()).hasSize(1);
        verifyNoInteractions(sessionFactory);
    }

    @Test
    void should_fail_when_model_answer_is_blocked_by_content_filter() {
        SessionFactory sessionFactory = mock(SessionFactory.class);
        MockChatModel chatModel = MockChatModel.thatResponds(emptyAnswer(FinishReason.CONTENT_FILTER));
        HibernateContentRetriever retriever = retriever(sessionFactory, chatModel);

        assertThatThrownBy(() -> retriever.retrieve(Query.from("How many customers are there?")))
                .isExactlyInstanceOf(ContentFilteredException.class)
                .hasMessageContaining("finish reason: CONTENT_FILTER");
        assertThat(chatModel.requests()).hasSize(1);
        verifyNoInteractions(sessionFactory);
    }

    private static HibernateContentRetriever retriever(SessionFactory sessionFactory, ChatModel chatModel) {
        return HibernateContentRetriever.builder()
                .sessionFactory(sessionFactory)
                .chatModel(chatModel)
                .databaseStructure("test")
                .maxRetries(1)
                .build();
    }

    private static ChatResponse emptyAnswer(FinishReason finishReason) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.builder().build())
                .finishReason(finishReason)
                .build();
    }
}
