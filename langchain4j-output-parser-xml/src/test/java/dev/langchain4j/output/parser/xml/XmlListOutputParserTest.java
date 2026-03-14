package dev.langchain4j.output.parser.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class XmlListOutputParserTest {

    static class Person {
        public String name;
        public int age;

        public Person() {}

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class Tag {
        public String name;

        public Tag() {}

        public Tag(String name) {
            this.name = name;
        }
    }

    @Test
    void should_parse_list_of_persons() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        String xml =
                """
                <persons>
                    <item><name>John</name><age>30</age></item>
                    <item><name>Jane</name><age>25</age></item>
                    <item><name>Bob</name><age>40</age></item>
                </persons>
                """;

        List<Person> result = parser.parse(xml);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).name).isEqualTo("John");
        assertThat(result.get(0).age).isEqualTo(30);
        assertThat(result.get(1).name).isEqualTo("Jane");
        assertThat(result.get(2).name).isEqualTo("Bob");
    }

    @Test
    void should_parse_list_from_markdown() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        String text =
                """
                Here are the people:
                ```xml
                <people>
                    <item><name>Alice</name><age>28</age></item>
                    <item><name>Charlie</name><age>35</age></item>
                </people>
                ```
                """;

        List<Person> result = parser.parse(text);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name).isEqualTo("Alice");
        assertThat(result.get(1).name).isEqualTo("Charlie");
    }

    @Test
    void should_parse_empty_list() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        String xml = "<persons></persons>";

        List<Person> result = parser.parse(xml);

        assertThat(result).isEmpty();
    }

    @Test
    void should_parse_single_item_list() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        String xml = "<persons><item><name>Solo</name><age>42</age></item></persons>";

        List<Person> result = parser.parse(xml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name).isEqualTo("Solo");
    }

    @Test
    void should_generate_format_instructions() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        String instructions = parser.formatInstructions();

        assertThat(instructions)
                .contains("You must answer strictly in the following XML format:")
                .contains("<persons>")
                .contains("Repeat the following element for each item")
                .contains("<person>")
                .contains("<name>");
    }

    @Test
    void should_throw_when_text_is_null() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void should_throw_when_text_is_blank() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void should_throw_when_no_xml_found() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        assertThatThrownBy(() -> parser.parse("Just plain text"))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("No valid XML found");
    }

    @Test
    void should_return_element_type() {
        XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);

        assertThat(parser.elementType()).isEqualTo(Person.class);
    }

    @Test
    void should_preserve_order() {
        XmlListOutputParser<Tag> parser = new XmlListOutputParser<>(Tag.class);

        String xml =
                """
                <tags>
                    <item><name>first</name></item>
                    <item><name>second</name></item>
                    <item><name>third</name></item>
                </tags>
                """;

        List<Tag> result = parser.parse(xml);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).name).isEqualTo("first");
        assertThat(result.get(1).name).isEqualTo("second");
        assertThat(result.get(2).name).isEqualTo("third");
    }
}
