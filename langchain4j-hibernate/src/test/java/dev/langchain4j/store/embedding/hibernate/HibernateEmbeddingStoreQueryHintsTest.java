package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hibernate.jpa.HibernateHints.HINT_COMMENT;
import static org.hibernate.jpa.HibernateHints.HINT_QUERY_DATABASE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.query.SelectionQuery;
import org.junit.jupiter.api.Test;

class HibernateEmbeddingStoreQueryHintsTest {

    @Test
    void should_apply_each_database_hint_of_a_list_separately() {
        SelectionQuery<?> query = mock(SelectionQuery.class);

        HibernateEmbeddingStore.applyHints(query, Map.of(HINT_QUERY_DATABASE, List.of("first", "second")));

        verify(query).setHint(HINT_QUERY_DATABASE, "first");
        verify(query).setHint(HINT_QUERY_DATABASE, "second");
        verifyNoMoreInteractions(query);
    }

    @Test
    void should_apply_each_database_hint_of_a_set_separately() {
        SelectionQuery<?> query = mock(SelectionQuery.class);

        HibernateEmbeddingStore.applyHints(query, Map.of(HINT_QUERY_DATABASE, Set.of("only")));

        verify(query).setHint(HINT_QUERY_DATABASE, "only");
        verifyNoMoreInteractions(query);
    }

    @Test
    void should_pass_other_hints_as_they_are() {
        SelectionQuery<?> query = mock(SelectionQuery.class);
        List<String> commentValue = List.of("a", "b");
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put(HINT_QUERY_DATABASE, "consistencyLevel=STRONG");
        hints.put(HINT_COMMENT, commentValue);

        HibernateEmbeddingStore.applyHints(query, hints);

        verify(query).setHint(HINT_QUERY_DATABASE, "consistencyLevel=STRONG");
        verify(query).setHint(HINT_COMMENT, commentValue);
        verifyNoMoreInteractions(query);
    }

    @Test
    void should_apply_nothing_without_hints() {
        SelectionQuery<?> query = mock(SelectionQuery.class);

        HibernateEmbeddingStore.applyHints(query, Map.of());

        verifyNoInteractions(query);
    }

    @Test
    void should_use_the_default_hints_when_no_hints_are_configured() {
        Map<String, Object> hints = HibernateEmbeddingStore.withDefaultHints(
                Map.of(HINT_QUERY_DATABASE, "consistencyLevel=SESSION"), null);

        assertThat(hints).containsExactly(entry(HINT_QUERY_DATABASE, "consistencyLevel=SESSION"));
    }

    @Test
    void should_replace_a_default_hint_with_a_configured_hint_of_the_same_name() {
        Map<String, Object> hints = HibernateEmbeddingStore.withDefaultHints(
                Map.of(HINT_QUERY_DATABASE, "consistencyLevel=SESSION"),
                Map.of(HINT_QUERY_DATABASE, "consistencyLevel=STRONG"));

        assertThat(hints).containsExactly(entry(HINT_QUERY_DATABASE, "consistencyLevel=STRONG"));
    }

    @Test
    void should_keep_the_default_hints_that_are_not_configured() {
        Map<String, Object> hints = HibernateEmbeddingStore.withDefaultHints(
                Map.of(HINT_QUERY_DATABASE, "consistencyLevel=SESSION"), Map.of(HINT_COMMENT, "search"));

        assertThat(hints)
                .containsOnly(entry(HINT_QUERY_DATABASE, "consistencyLevel=SESSION"), entry(HINT_COMMENT, "search"));
    }

    @Test
    void should_have_no_default_hints_for_the_built_in_database_kinds() {
        List<DatabaseKind> builtInKinds = List.of(
                DatabaseKind.DB2,
                DatabaseKind.MARIADB,
                DatabaseKind.MSSQL,
                DatabaseKind.MYSQL,
                DatabaseKind.POSTGRESQL,
                DatabaseKind.COCKROACHDB,
                DatabaseKind.ORACLE,
                DatabaseKind.HANA);

        for (DatabaseKind databaseKind : builtInKinds) {
            assertThat(databaseKind.defaultQueryHints()).isEmpty();
        }
    }
}
