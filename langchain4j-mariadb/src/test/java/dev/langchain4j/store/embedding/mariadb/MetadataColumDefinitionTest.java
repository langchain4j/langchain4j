package dev.langchain4j.store.embedding.mariadb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MetadataColumDefinitionTest {

    @Test
    void should_parse_type_in_a_locale_independent_way() {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(MetadataColumDefinition.from("mycol INT", List.of()).type())
                    .isEqualTo("int");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void should_parse_column_definition() {
        MetadataColumDefinition definition = MetadataColumDefinition.from("mycol INT", List.of());

        assertThat(definition.name()).isEqualTo("mycol");
        assertThat(definition.escapedName()).isEqualTo("mycol");
        assertThat(definition.type()).isEqualTo("int");
        assertThat(definition.fullDefinition()).isEqualTo("mycol INT");
    }

    @Test
    void should_parse_quoted_column_definition() {
        MetadataColumDefinition definition = MetadataColumDefinition.from("`my col` INT", List.of());

        assertThat(definition.name()).isEqualTo("my col");
        assertThat(definition.escapedName()).isEqualTo("`my col`");
        assertThat(definition.type()).isEqualTo("int");
    }

    @Test
    void should_reject_definition_without_type() {
        assertThatThrownBy(() -> MetadataColumDefinition.from("mycol", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
