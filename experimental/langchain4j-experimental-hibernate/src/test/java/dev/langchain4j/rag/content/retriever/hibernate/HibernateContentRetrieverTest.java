package dev.langchain4j.rag.content.retriever.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import dev.langchain4j.model.chat.ChatModel;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;

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
}
