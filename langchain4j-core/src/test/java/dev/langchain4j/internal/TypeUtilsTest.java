package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TypeUtilsTest {

    @Test
    void stringIsJsonCompatible() {
        assertThat(TypeUtils.isJsonString(char.class)).isTrue();
        assertThat(TypeUtils.isJsonString(String.class)).isTrue();
        assertThat(TypeUtils.isJsonString(Character.class)).isTrue();
        assertThat(TypeUtils.isJsonString(StringBuffer.class)).isTrue();
        assertThat(TypeUtils.isJsonString(StringBuilder.class)).isTrue();
        assertThat(TypeUtils.isJsonString(CharSequence.class)).isTrue();
        assertThat(TypeUtils.isJsonString(UUID.class)).isTrue();
    }

    @Test
    void collectionIsJsonCompatible() {
        assertThat(TypeUtils.isJsonArray(String[].class)).isTrue();
        assertThat(TypeUtils.isJsonArray(Integer[].class)).isTrue();
        assertThat(TypeUtils.isJsonArray(int[].class)).isTrue();

        assertThat(TypeUtils.isJsonArray(List.class)).isTrue();
        assertThat(TypeUtils.isJsonArray(Set.class)).isTrue();
        assertThat(TypeUtils.isJsonArray(Deque.class)).isTrue();
        assertThat(TypeUtils.isJsonArray(Collection.class)).isTrue();
        assertThat(TypeUtils.isJsonArray(Iterable.class)).isTrue();
    }
}
