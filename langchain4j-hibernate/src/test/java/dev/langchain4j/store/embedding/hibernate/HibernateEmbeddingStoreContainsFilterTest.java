package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;
import org.hibernate.query.criteria.JpaPath;
import org.hibernate.query.criteria.JpaPredicate;
import org.hibernate.query.criteria.JpaRoot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the SQL LIKE pattern produced by {@code HibernateEmbeddingStore.mapContains} when translating a
 * {@link ContainsString} metadata filter. These tests run without a live database: the store instance is created via
 * Mockito (bypassing the regular constructor) and {@code mapContains} is invoked through reflection so the real method
 * body executes against mocked Hibernate criteria collaborators.
 */
class HibernateEmbeddingStoreContainsFilterTest {

    @SuppressWarnings("unchecked")
    private String capturePattern(String comparisonValue) throws Exception {
        HibernateEmbeddingStore<?> store = mock(HibernateEmbeddingStore.class, CALLS_REAL_METHODS);

        // Inject a mapped attribute for key "key" so mapContains takes the mapped branch (avoids the unmapped
        // branch which dereferences the null unmappedMetadataAttributeMapping field).
        Field mappingsField = HibernateEmbeddingStore.class.getDeclaredField("metadataAttributeMappings");
        mappingsField.setAccessible(true);
        mappingsField.set(store, Map.of("key", mock(AttributeMapping.class)));

        JpaRoot<Object> root = mock(JpaRoot.class);
        HibernateCriteriaBuilder cb = mock(HibernateCriteriaBuilder.class);
        JpaPath<Object> path = mock(JpaPath.class);
        JpaPredicate notNullPredicate = mock(JpaPredicate.class);
        JpaPredicate likePredicate = mock(JpaPredicate.class);
        JpaPredicate andPredicate = mock(JpaPredicate.class);

        when(root.get("key")).thenReturn(path);
        when(path.isNotNull()).thenReturn(notNullPredicate);

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        when(cb.like(eq((JpaExpression<String>) (JpaExpression<?>) path), patternCaptor.capture(), eq('\\')))
                .thenReturn(likePredicate);
        when(cb.and(notNullPredicate, likePredicate)).thenReturn(andPredicate);

        ContainsString containsString = new ContainsString("key", comparisonValue);

        Method mapContains = HibernateEmbeddingStore.class.getDeclaredMethod(
                "mapContains", JpaRoot.class, HibernateCriteriaBuilder.class, ContainsString.class);
        mapContains.setAccessible(true);
        JpaPredicate result = (JpaPredicate) mapContains.invoke(store, root, cb, containsString);

        assertThat(result).isSameAs(andPredicate);
        return patternCaptor.getValue();
    }

    @Test
    void escapes_single_character_wildcard_underscore() throws Exception {
        assertThat(capturePattern("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void escapes_multi_character_wildcard_percent() throws Exception {
        assertThat(capturePattern("a%b")).isEqualTo("%a\\%b%");
    }

    @Test
    void does_not_escape_question_mark_which_is_not_a_like_wildcard() throws Exception {
        assertThat(capturePattern("a?b")).isEqualTo("%a?b%");
    }

    @Test
    void escapes_backslash() throws Exception {
        assertThat(capturePattern("a\\b")).isEqualTo("%a\\\\b%");
    }
}
