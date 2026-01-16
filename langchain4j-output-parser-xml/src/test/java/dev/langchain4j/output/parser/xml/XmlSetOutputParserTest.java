package dev.langchain4j.output.parser.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class XmlSetOutputParserTest {

    static class Tag {
        public String name;

        public Tag() {}

        public Tag(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tag tag = (Tag) o;
            return Objects.equals(name, tag.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    static class Category {
        public String id;
        public String label;

        public Category() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Category category = (Category) o;
            return Objects.equals(id, category.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Test
    void should_parse_set_of_tags() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        String xml =
                """
                <tags>
                    <item><name>java</name></item>
                    <item><name>xml</name></item>
                    <item><name>parser</name></item>
                </tags>
                """;

        Set<Tag> result = parser.parse(xml);

        assertThat(result).hasSize(3).extracting(t -> t.name).containsExactlyInAnyOrder("java", "xml", "parser");
    }

    @Test
    void should_remove_duplicates() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        String xml =
                """
                <tags>
                    <item><name>java</name></item>
                    <item><name>xml</name></item>
                    <item><name>java</name></item>
                    <item><name>java</name></item>
                </tags>
                """;

        Set<Tag> result = parser.parse(xml);

        assertThat(result).hasSize(2).extracting(t -> t.name).containsExactlyInAnyOrder("java", "xml");
    }

    @Test
    void should_parse_from_markdown() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        String text =
                """
                Here are the unique tags:
                ```xml
                <tags>
                    <item><name>spring</name></item>
                    <item><name>boot</name></item>
                </tags>
                ```
                """;

        Set<Tag> result = parser.parse(text);

        assertThat(result).hasSize(2);
    }

    @Test
    void should_parse_empty_set() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        String xml = "<tags></tags>";

        Set<Tag> result = parser.parse(xml);

        assertThat(result).isEmpty();
    }

    @Test
    void should_generate_format_instructions() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        String instructions = parser.formatInstructions();

        assertThat(instructions)
                .contains("You must answer strictly in the following XML format:")
                .contains("<tags>")
                .contains("Repeat the following element for each item");
    }

    @Test
    void should_throw_when_text_is_null() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void should_throw_when_no_xml_found() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        assertThatThrownBy(() -> parser.parse("No XML here"))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("No valid XML found");
    }

    @Test
    void should_return_element_type() {
        XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);

        assertThat(parser.elementType()).isEqualTo(Tag.class);
    }

    @Test
    void should_preserve_insertion_order() {
        XmlSetOutputParser<Category> parser = new XmlSetOutputParser<>(Category.class);

        String xml =
                """
                <categories>
                    <item><id>1</id><label>First</label></item>
                    <item><id>2</id><label>Second</label></item>
                    <item><id>3</id><label>Third</label></item>
                </categories>
                """;

        Set<Category> result = parser.parse(xml);

        // LinkedHashSet preserves insertion order
        assertThat(result).hasSize(3);
        assertThat(result.iterator().next().id).isEqualTo("1");
    }
}
