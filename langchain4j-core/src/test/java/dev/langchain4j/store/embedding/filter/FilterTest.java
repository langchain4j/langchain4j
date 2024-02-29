package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.store.embedding.filter.Filter.Key.key;
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
        assertThat(key("id").in("1").test(metadata)).isTrue();
        assertThat(key("id").in(singletonList("1")).test(metadata)).isTrue();
        assertThat(key("id").in("1", "2").test(metadata)).isTrue();
        assertThat(key("id").in(asList("1", "2")).test(metadata)).isTrue();

        assertThat(key("id").in("2").test(metadata)).isFalse();
        assertThat(key("id").in("2", "3").test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").in(1).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.String, while comparison value (1) has type java.lang.Integer");
    }

    @Test
    void test_not_in_string() {

        // given
        Metadata metadata = new Metadata().put("id", "1");

        // when-then
        assertThat(key("id").nin("2").test(metadata)).isTrue();
        assertThat(key("id").nin(singletonList("2")).test(metadata)).isTrue();
        assertThat(key("id").nin("2", "3").test(metadata)).isTrue();
        assertThat(key("id").nin(asList("2", "3")).test(metadata)).isTrue();

        assertThat(key("id").nin("1").test(metadata)).isFalse();
        assertThat(key("id").nin("1", "2").test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").nin(1).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.String, while comparison value (1) has type java.lang.Integer");
    }

    @Test
    void test_in_integer() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        // when-then
        assertThat(key("id").in(1).test(metadata)).isTrue();
        assertThat(key("id").in(singletonList(1)).test(metadata)).isTrue();
        assertThat(key("id").in(1, 2).test(metadata)).isTrue();
        assertThat(key("id").in(asList(1, 2)).test(metadata)).isTrue();
        assertThat(key("id").in(1L).test(metadata)).isTrue();
        assertThat(key("id").in(1f).test(metadata)).isTrue();
        assertThat(key("id").in(1d).test(metadata)).isTrue();

        assertThat(key("id").in(2).test(metadata)).isFalse();
        assertThat(key("id").in(2, 3).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").in("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Integer, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_integer() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        // when-then
        assertThat(key("id").nin(2).test(metadata)).isTrue();
        assertThat(key("id").nin(singletonList(2)).test(metadata)).isTrue();
        assertThat(key("id").nin(2, 3).test(metadata)).isTrue();
        assertThat(key("id").nin(asList(2, 3)).test(metadata)).isTrue();

        assertThat(key("id").nin(1).test(metadata)).isFalse();
        assertThat(key("id").nin(1, 2).test(metadata)).isFalse();
        assertThat(key("id").nin(1L).test(metadata)).isFalse();
        assertThat(key("id").nin(1f).test(metadata)).isFalse();
        assertThat(key("id").nin(1d).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").nin("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Integer, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_long() {

        // given
        Metadata metadata = new Metadata().put("id", 1L);

        // when-then
        assertThat(key("id").in(1L).test(metadata)).isTrue();
        assertThat(key("id").in(singletonList(1L)).test(metadata)).isTrue();
        assertThat(key("id").in(1L, 2L).test(metadata)).isTrue();
        assertThat(key("id").in(asList(1L, 2L)).test(metadata)).isTrue();
        assertThat(key("id").in(1).test(metadata)).isTrue();
        assertThat(key("id").in(1f).test(metadata)).isTrue();
        assertThat(key("id").in(1d).test(metadata)).isTrue();

        assertThat(key("id").in(2L).test(metadata)).isFalse();
        assertThat(key("id").in(2L, 3L).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").in("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Long, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_long() {

        // given
        Metadata metadata = new Metadata().put("id", 1L);

        // when-then
        assertThat(key("id").nin(2L).test(metadata)).isTrue();
        assertThat(key("id").nin(singletonList(2L)).test(metadata)).isTrue();
        assertThat(key("id").nin(2L, 3L).test(metadata)).isTrue();
        assertThat(key("id").nin(asList(2L, 3L)).test(metadata)).isTrue();

        assertThat(key("id").nin(1L).test(metadata)).isFalse();
        assertThat(key("id").nin(1L, 2L).test(metadata)).isFalse();
        assertThat(key("id").nin(1).test(metadata)).isFalse();
        assertThat(key("id").nin(1f).test(metadata)).isFalse();
        assertThat(key("id").nin(1d).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").nin("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1) " +
                        "has type java.lang.Long, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_float() {

        // given
        Metadata metadata = new Metadata().put("id", 1f);

        // when-then
        assertThat(key("id").in(1f).test(metadata)).isTrue();
        assertThat(key("id").in(singletonList(1f)).test(metadata)).isTrue();
        assertThat(key("id").in(1f, 2f).test(metadata)).isTrue();
        assertThat(key("id").in(asList(1f, 2f)).test(metadata)).isTrue();
        assertThat(key("id").in(1).test(metadata)).isTrue();
        assertThat(key("id").in(1L).test(metadata)).isTrue();
        assertThat(key("id").in(1d).test(metadata)).isTrue();

        assertThat(key("id").in(2f).test(metadata)).isFalse();
        assertThat(key("id").in(2f, 3f).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").in("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Float, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_float() {

        // given
        Metadata metadata = new Metadata().put("id", 1f);

        // when-then
        assertThat(key("id").nin(2f).test(metadata)).isTrue();
        assertThat(key("id").nin(singletonList(2f)).test(metadata)).isTrue();
        assertThat(key("id").nin(2f, 3f).test(metadata)).isTrue();
        assertThat(key("id").nin(asList(2f, 3f)).test(metadata)).isTrue();

        assertThat(key("id").nin(1f).test(metadata)).isFalse();
        assertThat(key("id").nin(1f, 2f).test(metadata)).isFalse();
        assertThat(key("id").nin(1).test(metadata)).isFalse();
        assertThat(key("id").nin(1L).test(metadata)).isFalse();
        assertThat(key("id").nin(1d).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").nin("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Float, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_double() {

        // given
        Metadata metadata = new Metadata().put("id", 1d);

        // when-then
        assertThat(key("id").in(1d).test(metadata)).isTrue();
        assertThat(key("id").in(singletonList(1d)).test(metadata)).isTrue();
        assertThat(key("id").in(1d, 2d).test(metadata)).isTrue();
        assertThat(key("id").in(asList(1d, 2d)).test(metadata)).isTrue();
        assertThat(key("id").in(1).test(metadata)).isTrue();
        assertThat(key("id").in(1L).test(metadata)).isTrue();
        assertThat(key("id").in(1f).test(metadata)).isTrue();

        assertThat(key("id").in(2d).test(metadata)).isFalse();
        assertThat(key("id").in(2d, 3d).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").in("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Double, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_not_in_double() {

        // given
        Metadata metadata = new Metadata().put("id", 1d);

        // when-then
        assertThat(key("id").nin(2d).test(metadata)).isTrue();
        assertThat(key("id").nin(singletonList(2d)).test(metadata)).isTrue();
        assertThat(key("id").nin(2d, 3d).test(metadata)).isTrue();
        assertThat(key("id").nin(asList(2d, 3d)).test(metadata)).isTrue();

        assertThat(key("id").nin(1d).test(metadata)).isFalse();
        assertThat(key("id").nin(1d, 2d).test(metadata)).isFalse();
        assertThat(key("id").nin(1).test(metadata)).isFalse();
        assertThat(key("id").nin(1L).test(metadata)).isFalse();
        assertThat(key("id").nin(1f).test(metadata)).isFalse();

        assertThatThrownBy(() -> key("id").nin("1").test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Type mismatch: actual value of metadata key \"id\" (1.0) " +
                        "has type java.lang.Double, while comparison value (1) has type java.lang.String");
    }

    @Test
    void test_in_empty_list() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> key("id").in(emptyList()).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValues with key 'id' cannot be null or empty");
    }

    @Test
    void test_in_list_with_null() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> key("id").in(asList(1, null)).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValue with key 'id' cannot be null");
    }

    @Test
    void test_not_in_empty_list() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> key("id").nin(emptyList()).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValues with key 'id' cannot be null or empty");
    }

    @Test
    void test_not_in_list_with_null() {

        // given
        Metadata metadata = new Metadata().put("id", 1);

        assertThatThrownBy(() -> key("id").nin(asList(1, null)).test(metadata))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("comparisonValue with key 'id' cannot be null");
    }
}