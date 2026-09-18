package dev.langchain4j.store.embedding.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskingTest {

    @Test
    void builder_toString_should_mask_password() {
        String toString =
                HibernateEmbeddingStore.dynamicBuilder().password("super-secret").toString();

        assertThat(toString).doesNotContain("super-secret").contains("password=********");
    }

    @Test
    void builder_toString_should_render_null_password_as_null() {
        String toString = HibernateEmbeddingStore.dynamicBuilder().toString();

        assertThat(toString).contains("password=null");
    }
}
