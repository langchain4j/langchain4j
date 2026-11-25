package dev.langchain4j.store.embedding.infinispan;

import static dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_BASIC;
import static dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_FULLTEXT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfinispanStoreConfigurationTest {

    @Test
    void should_keep_the_basic_text_annotation_by_default() {
        InfinispanStoreConfiguration configuration =
                new InfinispanStoreConfiguration("cache", 384, null, null, null, null, null, null, null, true, true);

        assertThat(configuration.textFieldAnnotation()).isEqualTo(TEXT_FIELD_ANNOTATION_BASIC);
        assertThat(LangchainSchemaCreator.buildSchema(configuration).getContent())
                .contains(TEXT_FIELD_ANNOTATION_BASIC)
                .doesNotContain("@Text");
    }

    @Test
    void should_use_the_given_text_annotation() {
        InfinispanStoreConfiguration configuration = new InfinispanStoreConfiguration(
                "cache", 384, null, null, null, null, null, null, null, true, true, TEXT_FIELD_ANNOTATION_FULLTEXT);

        assertThat(LangchainSchemaCreator.buildSchema(configuration).getContent())
                .contains(TEXT_FIELD_ANNOTATION_FULLTEXT);
    }
}
