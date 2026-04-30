package dev.langchain4j.output.parser.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;
import org.junit.jupiter.api.Test;

class XmlOutputParserTest {

    // Test model classes
    static class Person {
        public String name;
        public int age;
        public String email;

        public Person() {}

        public Person(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }
    }

    @JacksonXmlRootElement(localName = "customer")
    static class AnnotatedPerson {
        @JacksonXmlProperty(localName = "full-name")
        @Description("The customer's full legal name")
        public String name;

        @JacksonXmlProperty(isAttribute = true)
        public int age;

        public AnnotatedPerson() {}
    }

    static class Order {
        public String orderId;
        public Customer customer;
        public List<LineItem> items;

        public Order() {}
    }

    static class Customer {
        public String name;
        public String email;

        public Customer() {}
    }

    static class LineItem {
        public String product;
        public int quantity;
        public double price;

        public LineItem() {}
    }

    @Test
    void should_parse_simple_xml() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String xml = "<person><name>John</name><age>30</age><email>john@example.com</email></person>";

        Person result = parser.parse(xml);

        assertThat(result.name).isEqualTo("John");
        assertThat(result.age).isEqualTo(30);
        assertThat(result.email).isEqualTo("john@example.com");
    }

    @Test
    void should_parse_xml_with_different_root_element() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        // Parser should handle any root element name
        String xml = "<data><name>Jane</name><age>25</age></data>";

        Person result = parser.parse(xml);

        assertThat(result.name).isEqualTo("Jane");
        assertThat(result.age).isEqualTo(25);
    }

    @Test
    void should_extract_xml_from_markdown() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String text =
                """
                Here is the person data:
                ```xml
                <person><name>Jane</name><age>25</age></person>
                ```
                Hope this helps!
                """;

        Person result = parser.parse(text);

        assertThat(result.name).isEqualTo("Jane");
        assertThat(result.age).isEqualTo(25);
    }

    @Test
    void should_extract_xml_from_mixed_content() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String text =
                """
                Based on the text, I extracted the following:
                <person><name>Bob</name><age>40</age></person>
                This represents the person mentioned.
                """;

        Person result = parser.parse(text);

        assertThat(result.name).isEqualTo("Bob");
        assertThat(result.age).isEqualTo(40);
    }

    @Test
    void should_parse_nested_objects() {
        XmlOutputParser<Order> parser = new XmlOutputParser<>(Order.class);

        String xml =
                """
                <order>
                    <orderId>ORD-123</orderId>
                    <customer>
                        <name>Alice</name>
                        <email>alice@example.com</email>
                    </customer>
                </order>
                """;

        Order result = parser.parse(xml);

        assertThat(result.orderId).isEqualTo("ORD-123");
        assertThat(result.customer).isNotNull();
        assertThat(result.customer.name).isEqualTo("Alice");
        assertThat(result.customer.email).isEqualTo("alice@example.com");
    }

    @Test
    void should_parse_annotated_class_with_attributes() {
        XmlOutputParser<AnnotatedPerson> parser = new XmlOutputParser<>(AnnotatedPerson.class);

        String xml = "<customer age=\"35\"><full-name>John Smith</full-name></customer>";

        AnnotatedPerson result = parser.parse(xml);

        assertThat(result.name).isEqualTo("John Smith");
        assertThat(result.age).isEqualTo(35);
    }

    @Test
    void should_ignore_unknown_properties() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String xml = "<person><name>John</name><age>30</age><unknownField>ignored</unknownField></person>";

        Person result = parser.parse(xml);

        assertThat(result.name).isEqualTo("John");
        assertThat(result.age).isEqualTo(30);
    }

    @Test
    void should_generate_format_instructions() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String instructions = parser.formatInstructions();

        assertThat(instructions).contains("You must answer strictly in the following XML format:");
        assertThat(instructions).contains("<person>");
        assertThat(instructions).contains("<name>");
        assertThat(instructions).contains("(string)");
        assertThat(instructions).contains("(integer)");
    }

    @Test
    void should_generate_format_instructions_for_annotated_class() {
        XmlOutputParser<AnnotatedPerson> parser = new XmlOutputParser<>(AnnotatedPerson.class);

        String instructions = parser.formatInstructions();

        assertThat(instructions).contains("<customer");
        assertThat(instructions).contains("age=");
        assertThat(instructions).contains("<full-name>");
        assertThat(instructions).contains("The customer's full legal name");
    }

    @Test
    void should_throw_when_text_is_null() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void should_throw_when_text_is_blank() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void should_throw_when_no_xml_found() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        assertThatThrownBy(() -> parser.parse("Just plain text without any XML"))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("No valid XML found");
    }

    @Test
    void should_throw_when_xml_is_malformed() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        // Use XML with mismatched tags which Jackson cannot parse
        assertThatThrownBy(() -> parser.parse("<person><name>John</wrong></person>"))
                .isInstanceOf(XmlOutputParsingException.class);
    }

    @Test
    void should_use_custom_xml_mapper() {
        XmlMapper customMapper = XmlMapper.builder().build();
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class, customMapper);

        String xml = "<person><name>John</name><age>30</age></person>";

        Person result = parser.parse(xml);

        assertThat(result.name).isEqualTo("John");
    }

    @Test
    void should_return_target_type() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        assertThat(parser.targetType()).isEqualTo(Person.class);
    }

    @Test
    void should_handle_xml_with_whitespace() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String xml =
                """

                  <person>
                    <name>  John  </name>
                    <age>30</age>
                  </person>

                """;

        Person result = parser.parse(xml);

        assertThat(result.name).isEqualTo("  John  ");
        assertThat(result.age).isEqualTo(30);
    }

    @Test
    void should_handle_missing_optional_fields() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);

        String xml = "<person><name>John</name><age>30</age></person>";

        Person result = parser.parse(xml);

        assertThat(result.name).isEqualTo("John");
        assertThat(result.age).isEqualTo(30);
        assertThat(result.email).isNull();
    }
}
