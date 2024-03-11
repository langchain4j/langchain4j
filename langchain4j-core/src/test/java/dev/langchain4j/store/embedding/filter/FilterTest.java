package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterTest {

    @Test
    void test_in_string() {

        // given
        Metadata metadata = new Metadata().put("id", "1");

        // when-then
        assertThat(metadataKey("id").isIn("1").test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(singletonList("1")).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn("1", "2").test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(asList("1", "2")).test(metadata)).isTrue();

        assertThat(metadataKey("id").isIn("2").test(metadata)).isFalse();
        assertThat(metadataKey("id").isIn("2", "3").test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isIn(1).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.String, while comparison value (1) has type java.lang.Integer");
    }

    @Test
    void test_not_in_string() {

        // given
        Metadata metadata = new Metadata().put("id", "1");

        // when-then
        assertThat(metadataKey("id").isNotIn("2").test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(singletonList("2")).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn("2", "3").test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(asList("2", "3")).test(metadata)).isTrue();

        assertThat(metadataKey("id").isNotIn("1").test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn("1", "2").test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isNotIn(1).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.String, while comparison value (1) has type java.lang.Integer");
    }

    @Test
    void test_in_integer() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        // when-then
        assertThat(metadataKey("id").isIn(1).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(singletonList(1)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1, 2).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(asList(1, 2)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1f).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1d).test(metadata)).isTrue();

        assertThat(metadataKey("id").isIn(2).test(metadata)).isFalse();
        assertThat(metadataKey("id").isIn(2, 3).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Integer, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_integer() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        // when-then
        assertThat(metadataKey("id").isNotIn(2).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(singletonList(2)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(2, 3).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(asList(2, 3)).test(metadata)).isTrue();

        assertThat(metadataKey("id").isNotIn(1).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1, 2).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1L).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1f).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1d).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isNotIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Integer, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_long() {

        // given
        Metadata metadata = new Metadata().put("id", 1L);

        // when-then
        assertThat(metadataKey("id").isIn(1L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(singletonList(1L)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1L, 2L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(asList(1L, 2L)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1f).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1d).test(metadata)).isTrue();

        assertThat(metadataKey("id").isIn(2L).test(metadata)).isFalse();
        assertThat(metadataKey("id").isIn(2L, 3L).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Long, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_long() {

        // given
        Metadata metadata = new Metadata().put("id", 1L);

        // when-then
        assertThat(metadataKey("id").isNotIn(2L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(singletonList(2L)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(2L, 3L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(asList(2L, 3L)).test(metadata)).isTrue();

        assertThat(metadataKey("id").isNotIn(1L).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1L, 2L).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1f).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1d).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isNotIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Long, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_float() {

        // given
        Metadata metadata = new Metadata().put("id", 1f);

        // when-then
        assertThat(metadataKey("id").isIn(1f).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(singletonList(1f)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1f, 2f).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(asList(1f, 2f)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1d).test(metadata)).isTrue();

        assertThat(metadataKey("id").isIn(2f).test(metadata)).isFalse();
        assertThat(metadataKey("id").isIn(2f, 3f).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Float, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_float() {

        // given
        Metadata metadata = new Metadata().put("id", 1f);

        // when-then
        assertThat(metadataKey("id").isNotIn(2f).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(singletonList(2f)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(2f, 3f).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(asList(2f, 3f)).test(metadata)).isTrue();

        assertThat(metadataKey("id").isNotIn(1f).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1f, 2f).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1L).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1d).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isNotIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Float, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_double() {

        // given
        Metadata metadata = new Metadata().put("id", 1d);

        // when-then
        assertThat(metadataKey("id").isIn(1d).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(singletonList(1d)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1d, 2d).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(asList(1d, 2d)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1L).test(metadata)).isTrue();
        assertThat(metadataKey("id").isIn(1f).test(metadata)).isTrue();

        assertThat(metadataKey("id").isIn(2d).test(metadata)).isFalse();
        assertThat(metadataKey("id").isIn(2d, 3d).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Double, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_double() {

        // given
        Metadata metadata = new Metadata().put("id", 1d);

        // when-then
        assertThat(metadataKey("id").isNotIn(2d).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(singletonList(2d)).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(2d, 3d).test(metadata)).isTrue();
        assertThat(metadataKey("id").isNotIn(asList(2d, 3d)).test(metadata)).isTrue();

        assertThat(metadataKey("id").isNotIn(1d).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1d, 2d).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1L).test(metadata)).isFalse();
        assertThat(metadataKey("id").isNotIn(1f).test(metadata)).isFalse();

        assertThatThrownBy(() -> metadataKey("id").isNotIn("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Double, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_empty_list() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> metadataKey("id").isIn(emptyList()).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValues with key 'id' cannot be null or empty");
    }

    @Test
    void test_in_list_with_null() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> metadataKey("id").isIn(asList(1, null)).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValue with key 'id' cannot be null");
    }

    @Test
    void test_not_in_empty_list() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> metadataKey("id").isNotIn(emptyList()).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValues with key 'id' cannot be null or empty");
    }

    @Test
    void test_not_in_list_with_null() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> metadataKey("id").isNotIn(asList(1, null)).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValue with key 'id' cannot be null");
    }
}