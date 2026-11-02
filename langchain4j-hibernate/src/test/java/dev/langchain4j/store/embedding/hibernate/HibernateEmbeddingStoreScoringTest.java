package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import org.hibernate.SessionFactory;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaFunction;
import org.hibernate.query.criteria.JpaOrder;
import org.hibernate.query.criteria.JpaParameterExpression;
import org.hibernate.query.criteria.JpaPath;
import org.hibernate.query.criteria.JpaRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the translation between a raw vector distance, as returned by the database, and the {@code 0..1}
 * relevance score that {@code EmbeddingStore} reports. These tests run without a live database: the store instance is
 * created via Mockito (bypassing the regular constructor) and the private methods are invoked through reflection so the
 * real method bodies execute.
 */
class HibernateEmbeddingStoreScoringTest {

    private static final double[] SCORES = {0.01d, 0.1d, 0.25d, 0.5d, 0.75d, 0.9d, 0.99d};

    private static double scoreToDistance(
            HibernateEmbeddingStore<?> store, double score, DistanceFunction distanceFunction) throws Exception {
        return (double) invoke(store, "scoreToDistance", score, distanceFunction);
    }

    private static double scoreFromDistance(
            HibernateEmbeddingStore<?> store, double distance, DistanceFunction distanceFunction) throws Exception {
        return (double) invoke(store, "scoreFromDistance", distance, distanceFunction);
    }

    private static Object invoke(HibernateEmbeddingStore<?> store, String name, double value, DistanceFunction function)
            throws Exception {
        final Method method =
                HibernateEmbeddingStore.class.getDeclaredMethod(name, double.class, DistanceFunction.class);
        method.setAccessible(true);
        return method.invoke(store, value, function);
    }

    @SuppressWarnings("unchecked")
    private static HibernateEmbeddingStore<?> store() {
        return mock(HibernateEmbeddingStore.class, CALLS_REAL_METHODS);
    }

    @ParameterizedTest
    @EnumSource(DistanceFunction.class)
    void should_round_trip_a_score_through_a_distance(DistanceFunction distanceFunction) throws Exception {
        final HibernateEmbeddingStore<?> store = store();

        for (double score : SCORES) {
            final double distance = scoreToDistance(store, score, distanceFunction);
            assertThat(scoreFromDistance(store, distance, distanceFunction))
                    .as("round trip of score %s for %s", score, distanceFunction)
                    .isCloseTo(score, offset(1e-9d));
        }
    }

    @Test
    void should_score_inner_product_like_cosine_for_normalized_embeddings() throws Exception {
        final HibernateEmbeddingStore<?> store = store();

        for (double innerProduct : new double[] {-1d, -0.5d, 0d, 0.3d, 0.8d, 1d}) {
            // For normalized embeddings, the cosine distance is 1 - inner product
            final double cosineScore = scoreFromDistance(store, 1d - innerProduct, DistanceFunction.COSINE);

            assertThat(scoreFromDistance(store, innerProduct, DistanceFunction.INNER_PRODUCT))
                    .isCloseTo(cosineScore, offset(1e-9d));
            assertThat(scoreFromDistance(store, -innerProduct, DistanceFunction.NEGATIVE_INNER_PRODUCT))
                    .isCloseTo(cosineScore, offset(1e-9d));
        }
    }

    @Test
    void should_limit_the_inner_product_score_for_embeddings_that_are_not_normalized() throws Exception {
        final HibernateEmbeddingStore<?> store = store();

        assertThat(scoreFromDistance(store, 3d, DistanceFunction.INNER_PRODUCT)).isEqualTo(1d);
        assertThat(scoreFromDistance(store, -3d, DistanceFunction.INNER_PRODUCT)).isEqualTo(0d);
        assertThat(scoreFromDistance(store, -3d, DistanceFunction.NEGATIVE_INNER_PRODUCT))
                .isEqualTo(1d);
        assertThat(scoreFromDistance(store, 3d, DistanceFunction.NEGATIVE_INNER_PRODUCT))
                .isEqualTo(0d);
    }

    @ParameterizedTest
    @EnumSource(DistanceFunction.class)
    void should_translate_a_perfect_min_score_to_a_finite_distance(DistanceFunction distanceFunction)
            throws Exception {
        final HibernateEmbeddingStore<?> store = store();

        assertThat(scoreToDistance(store, 1d, distanceFunction)).isFinite();
    }

    @ParameterizedTest
    @EnumSource(DistanceFunction.class)
    void should_move_the_score_in_the_same_direction_as_the_ordering(DistanceFunction distanceFunction)
            throws Exception {
        final HibernateEmbeddingStore<?> store = store();

        final double scoreOfNearer = scoreFromDistance(store, 0.25d, distanceFunction);
        final double scoreOfFarther = scoreFromDistance(store, 0.75d, distanceFunction);

        if (distanceFunction == DistanceFunction.INNER_PRODUCT) {
            // A higher inner product is a better match, so the score has to grow with the distance value
            assertThat(scoreOfFarther).isGreaterThan(scoreOfNearer);
        } else {
            assertThat(scoreOfFarther).isLessThan(scoreOfNearer);
        }
    }

    @ParameterizedTest
    @EnumSource(DistanceFunction.class)
    void should_order_results_best_match_first(DistanceFunction distanceFunction) throws Exception {
        final HibernateEmbeddingStore<?> store = store();
        final HibernateCriteriaBuilder criteriaBuilder = givenCriteriaBuilder(store, distanceFunction);
        final Expression<Double> distance = givenDistanceExpression(criteriaBuilder);

        createBaseQuery(store);

        if (distanceFunction == DistanceFunction.INNER_PRODUCT) {
            // A higher inner product is a better match, so the best matches come last in ascending order
            verify(criteriaBuilder).desc(distance);
            verify(criteriaBuilder, never()).asc(any(Expression.class));
        } else {
            verify(criteriaBuilder).asc(distance);
            verify(criteriaBuilder, never()).desc(any(Expression.class));
        }
    }

    @ParameterizedTest
    @EnumSource(DistanceFunction.class)
    void should_keep_matches_on_the_better_side_of_the_min_distance(DistanceFunction distanceFunction)
            throws Exception {
        final HibernateEmbeddingStore<?> store = store();
        final HibernateCriteriaBuilder criteriaBuilder = mock(HibernateCriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        final Expression<Double> distance = mock(Expression.class);
        @SuppressWarnings("unchecked")
        final Expression<Double> minDistance = mock(Expression.class);

        final Method method = HibernateEmbeddingStore.class.getDeclaredMethod(
                "minDistanceFilter", DistanceFunction.class, Expression.class, Expression.class, CriteriaBuilder.class);
        method.setAccessible(true);
        method.invoke(store, distanceFunction, distance, minDistance, criteriaBuilder);

        if (distanceFunction == DistanceFunction.INNER_PRODUCT) {
            verify(criteriaBuilder).ge(distance, minDistance);
        } else {
            verify(criteriaBuilder).le(distance, minDistance);
        }
    }

    private static void createBaseQuery(HibernateEmbeddingStore<?> store) throws Exception {
        final Method method = HibernateEmbeddingStore.class.getDeclaredMethod(
                "createBaseQuery", Class.class, boolean.class, BiFunction.class);
        method.setAccessible(true);
        method.invoke(store, Object[].class, false, null);
    }

    private static HibernateCriteriaBuilder givenCriteriaBuilder(
            HibernateEmbeddingStore<?> store, DistanceFunction distanceFunction) throws Exception {
        final HibernateCriteriaBuilder criteriaBuilder = mock(HibernateCriteriaBuilder.class);
        final SessionFactory sessionFactory = mock(SessionFactory.class);
        when(sessionFactory.getCriteriaBuilder()).thenReturn(criteriaBuilder);

        final AttributeMapping embeddingAttributeMapping = mock(AttributeMapping.class);
        when(embeddingAttributeMapping.getAttributeName()).thenReturn("embedding");

        set(store, "sessionFactory", sessionFactory);
        set(store, "entityClass", Object.class);
        set(store, "embeddingAttributeMapping", embeddingAttributeMapping);
        set(store, "distanceFunction", distanceFunction);
        return criteriaBuilder;
    }

    @SuppressWarnings("unchecked")
    private static Expression<Double> givenDistanceExpression(HibernateCriteriaBuilder criteriaBuilder) {
        final JpaCriteriaQuery<Object[]> query = mock(JpaCriteriaQuery.class);
        final JpaRoot<Object> root = mock(JpaRoot.class);
        final JpaFunction<Double> distance = mock(JpaFunction.class);

        when(criteriaBuilder.createQuery(Object[].class)).thenReturn(query);
        when(query.from(Object.class)).thenReturn(root);
        when(root.get("embedding")).thenReturn(mock(JpaPath.class));
        when(criteriaBuilder.parameter(any(Class.class), anyString())).thenReturn(mock(JpaParameterExpression.class));
        when(criteriaBuilder.function(anyString(), eq(Double.class), any(Expression.class), any(Expression.class)))
                .thenReturn(distance);
        when(criteriaBuilder.asc(any(Expression.class))).thenReturn(mock(JpaOrder.class));
        when(criteriaBuilder.desc(any(Expression.class))).thenReturn(mock(JpaOrder.class));
        return distance;
    }

    private static void set(HibernateEmbeddingStore<?> store, String fieldName, Object value) throws Exception {
        final Field field = HibernateEmbeddingStore.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(store, value);
    }
}
