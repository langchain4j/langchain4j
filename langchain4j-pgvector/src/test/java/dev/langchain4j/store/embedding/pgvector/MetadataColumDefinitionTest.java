package dev.langchain4j.store.embedding.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MetadataColumDefinitionTest {

    @Test
    void should_parse_type_in_a_locale_independent_way() {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(MetadataColumDefinition.from("mycol INT").getType()).isEqualTo("int");
            assertThat(MetadataColumDefinition.from("myjson JSONB").getType()).isEqualTo("jsonb");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void should_parse_column_definition() {
        MetadataColumDefinition definition = MetadataColumDefinition.from("mycol INT");

        assertThat(definition.getName()).isEqualTo("mycol");
        assertThat(definition.getType()).isEqualTo("int");
        assertThat(definition.getFullDefinition()).isEqualTo("mycol INT");
    }

    @Test
    void should_reject_definition_without_type() {
        assertThatThrownBy(() -> MetadataColumDefinition.from("mycol")).isInstanceOf(IllegalArgumentException.class);
    }
}
