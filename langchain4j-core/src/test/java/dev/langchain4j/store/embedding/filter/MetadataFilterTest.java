package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.store.embedding.filter.MetadataFilter.MetadataKey.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataFilterTest {

    @Test
    void should_compare_strings() {

        // given
        Metadata klaus = new Metadata().add("name", "Klaus");

        assertThat(key("name").eq("Klaus").test(klaus)).isTrue();
        assertThat(key("name").eq("Alice").test(klaus)).isFalse();
        assertThatThrownBy(() -> key("name").eq(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").ne("Klaus").test(klaus)).isFalse();
        assertThat(key("name").ne("Alice").test(klaus)).isTrue();
        assertThatThrownBy(() -> key("name").ne(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").gt("Alice").test(klaus)).isTrue();
        assertThat(key("name").gt("Klaus").test(klaus)).isFalse();
        assertThat(key("name").gt("Zoe").test(klaus)).isFalse();
        assertThatThrownBy(() -> key("name").gt(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").gte("Alice").test(klaus)).isTrue();
        assertThat(key("name").gte("Klaus").test(klaus)).isTrue();
        assertThat(key("name").gte("Zoe").test(klaus)).isFalse();
        assertThatThrownBy(() -> key("name").gte(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").lt("Alice").test(klaus)).isFalse();
        assertThat(key("name").lt("Klaus").test(klaus)).isFalse();
        assertThat(key("name").lt("Zoe").test(klaus)).isTrue();
        assertThatThrownBy(() -> key("name").lt(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").lte("Alice").test(klaus)).isFalse();
        assertThat(key("name").lte("Klaus").test(klaus)).isTrue();
        assertThat(key("name").lte("Zoe").test(klaus)).isTrue();
        assertThatThrownBy(() -> key("name").lte(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").in("Klaus").test(klaus)).isTrue();
        assertThat(key("name").in("Alice").test(klaus)).isFalse();
        assertThat(key("name").in("Klaus", "Alice").test(klaus)).isTrue();
        assertThat(key("name").in("Alice", "Klaus").test(klaus)).isTrue();
        assertThatThrownBy(() -> key("name").in(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("name").nin("Klaus").test(klaus)).isFalse();
        assertThat(key("name").nin("Alice").test(klaus)).isTrue();
        assertThat(key("name").nin("Klaus", "Alice").test(klaus)).isFalse();
        assertThat(key("name").nin("Alice", "Klaus").test(klaus)).isFalse();
        assertThatThrownBy(() -> key("name").nin(18).test(klaus))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");
    }

    @Test
    void should_compare_integers() {

        // given
        Metadata adult = new Metadata().add("age", 18);

        assertThat(key("age").eq(18).test(adult)).isTrue();
        assertThat(key("age").eq(0).test(adult)).isFalse();
        assertThatThrownBy(() -> key("age").eq("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").ne(18).test(adult)).isFalse();
        assertThat(key("age").ne(0).test(adult)).isTrue();
        assertThatThrownBy(() -> key("age").ne("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").gt(0).test(adult)).isTrue();
        assertThat(key("age").gt(18).test(adult)).isFalse();
        assertThat(key("age").gt(100).test(adult)).isFalse();
        assertThatThrownBy(() -> key("age").gt("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").gte(0).test(adult)).isTrue();
        assertThat(key("age").gte(18).test(adult)).isTrue();
        assertThat(key("age").gte(100).test(adult)).isFalse();
        assertThatThrownBy(() -> key("age").gte("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").lt(0).test(adult)).isFalse();
        assertThat(key("age").lt(18).test(adult)).isFalse();
        assertThat(key("age").lt(100).test(adult)).isTrue();
        assertThatThrownBy(() -> key("age").lt("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").lte(0).test(adult)).isFalse();
        assertThat(key("age").lte(18).test(adult)).isTrue();
        assertThat(key("age").lte(100).test(adult)).isTrue();
        assertThatThrownBy(() -> key("age").lte("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").in(18).test(adult)).isTrue();
        assertThat(key("age").in(0).test(adult)).isFalse();
        assertThat(key("age").in(18, 0).test(adult)).isTrue();
        assertThat(key("age").in(0, 18).test(adult)).isTrue();
        assertThatThrownBy(() -> key("age").in("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");

        assertThat(key("age").nin(18).test(adult)).isFalse();
        assertThat(key("age").nin(0).test(adult)).isTrue();
        assertThat(key("age").nin(18, 0).test(adult)).isFalse();
        assertThat(key("age").nin(0, 18).test(adult)).isFalse();
        assertThatThrownBy(() -> key("age").nin("Klaus").test(adult))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type mismatch");
    }

    // TODO other types
    // TODO more tests, also check missing value, value type mismatch, auto-casting, etc
}