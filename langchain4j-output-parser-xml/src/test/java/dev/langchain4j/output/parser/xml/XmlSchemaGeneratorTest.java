package dev.langchain4j.output.parser.xml;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import dev.langchain4j.model.output.structured.Description;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class XmlSchemaGeneratorTest {

    // Test models
    static class SimplePerson {
        public String name;
        public int age;
        public String email;
    }

    @JacksonXmlRootElement(localName = "customer")
    static class AnnotatedPerson {
        @JacksonXmlProperty(localName = "full-name")
        @Description("The customer's full legal name")
        public String name;

        @JacksonXmlProperty(isAttribute = true)
        public int age;
    }

    static class Order {
        public String orderId;
        public Customer customer;
        public List<LineItem> items;
    }

    static class Customer {
        public String name;
        public String email;
    }

    static class LineItem {
        public String product;
        public int quantity;
        public double price;
    }

    static class PersonWithCollections {
        public String name;

        @JacksonXmlElementWrapper(localName = "addresses")
        @JacksonXmlProperty(localName = "address")
        public List<Address> addresses;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "tag")
        public List<String> tags;
    }

    static class Address {
        public String street;
        public String city;
    }

    static class WithDescriptions {
        @Description("The unique identifier")
        public String id;

        @Description({"Amount in USD", "with two decimal places"})
        public BigDecimal amount;
    }

    static class WithCData {
        public String title;

        @JacksonXmlCData
        @Description("HTML content that may contain special characters")
        public String htmlContent;
    }

    static class WithTypes {
        public String stringField;
        public int intField;
        public Integer integerField;
        public boolean booleanField;
        public double doubleField;
        public LocalDate dateField;
        public LocalDateTime dateTimeField;
        public UUID uuidField;
    }

    enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    static class Task {
        public String name;
        public Priority priority;
    }

    static class Employee {
        public String name;
        public Employee manager; // Self-reference
    }

    @Test
    void should_generate_simple_structure() {
        String result = XmlSchemaGenerator.generateFormatInstructions(SimplePerson.class);

        assertThat(result)
                .contains("You must answer strictly in the following XML format:")
                .contains("<simple-person>")
                .contains("<name>(string)</name>")
                .contains("<age>(integer)</age>")
                .contains("<email>(string)</email>")
                .contains("</simple-person>");
    }

    @Test
    void should_use_jackson_root_element_annotation() {
        String result = XmlSchemaGenerator.generateFormatInstructions(AnnotatedPerson.class);

        assertThat(result).contains("<customer").doesNotContain("<annotated-person>");
    }

    @Test
    void should_use_jackson_property_annotation() {
        String result = XmlSchemaGenerator.generateFormatInstructions(AnnotatedPerson.class);

        assertThat(result).contains("<full-name>").doesNotContain("<name>");
    }

    @Test
    void should_render_attributes() {
        String result = XmlSchemaGenerator.generateFormatInstructions(AnnotatedPerson.class);

        assertThat(result).contains("age=\"(integer)\"");
    }

    @Test
    void should_include_description_comments() {
        String result = XmlSchemaGenerator.generateFormatInstructions(AnnotatedPerson.class);

        assertThat(result).contains("The customer's full legal name");
    }

    @Test
    void should_handle_nested_objects() {
        String result = XmlSchemaGenerator.generateFormatInstructions(Order.class);

        assertThat(result)
                .contains("<order>")
                .contains("<customer>")
                .contains("<email>(string)</email>")
                .contains("</customer>");
    }

    @Test
    void should_handle_collections_with_wrapper() {
        String result = XmlSchemaGenerator.generateFormatInstructions(PersonWithCollections.class);

        assertThat(result).contains("<addresses>").contains("<address>").contains("Repeat for each item");
    }

    @Test
    void should_handle_collections_without_wrapper() {
        String result = XmlSchemaGenerator.generateFormatInstructions(PersonWithCollections.class);

        assertThat(result).contains("<tag>");
    }

    @Test
    void should_handle_multi_line_descriptions() {
        String result = XmlSchemaGenerator.generateFormatInstructions(WithDescriptions.class);

        assertThat(result).contains("Amount in USD with two decimal places");
    }

    @Test
    void should_handle_cdata_annotation() {
        String result = XmlSchemaGenerator.generateFormatInstructions(WithCData.class);

        assertThat(result).contains("<![CDATA[").contains("]]>");
    }

    @Test
    void should_generate_type_hints_for_primitives() {
        String result = XmlSchemaGenerator.generateFormatInstructions(WithTypes.class);

        assertThat(result)
                .contains("(string)")
                .contains("(integer)")
                .contains("(true or false)")
                .contains("(number)");
    }

    @Test
    void should_generate_date_format_hints() {
        String result = XmlSchemaGenerator.generateFormatInstructions(WithTypes.class);

        assertThat(result).contains("YYYY-MM-DD").contains("YYYY-MM-DDTHH:MM:SS");
    }

    @Test
    void should_generate_uuid_hint() {
        String result = XmlSchemaGenerator.generateFormatInstructions(WithTypes.class);

        assertThat(result).contains("(UUID)");
    }

    @Test
    void should_handle_enum_types() {
        String result = XmlSchemaGenerator.generateFormatInstructions(Task.class);

        assertThat(result)
                .contains("one of:")
                .contains("LOW")
                .contains("MEDIUM")
                .contains("HIGH")
                .contains("CRITICAL");
    }

    @Test
    void should_handle_circular_references() {
        String result = XmlSchemaGenerator.generateFormatInstructions(Employee.class);

        // Should not recurse infinitely - manager should show type hint, not expand
        assertThat(result).contains("<employee>").contains("<manager>").contains("(Employee)");
    }

    @Test
    void should_convert_camel_case_to_kebab_case() {
        String result = XmlSchemaGenerator.generateFormatInstructions(SimplePerson.class);

        assertThat(result).contains("<simple-person>");
    }

    @Test
    void should_generate_collection_format_instructions() {
        String result = XmlSchemaGenerator.generateCollectionFormatInstructions(SimplePerson.class, "people", "person");

        assertThat(result)
                .contains("<people>")
                .contains("<person>")
                .contains("Repeat the following element for each item")
                .contains("</people>");
    }

    @Test
    void should_skip_static_fields() {
        class WithStatic {
            public static String CONSTANT = "value";
            public String name;
        }

        String result = XmlSchemaGenerator.generateFormatInstructions(WithStatic.class);

        assertThat(result).doesNotContain("CONSTANT").contains("<name>");
    }

    @Test
    void should_skip_transient_fields() {
        class WithTransient {
            public String name;
            public transient String cached;
        }

        String result = XmlSchemaGenerator.generateFormatInstructions(WithTransient.class);

        assertThat(result).doesNotContain("cached").contains("<name>");
    }

    @Test
    void should_handle_arrays() {
        class WithArray {
            public String[] tags;
            public int[] scores;
        }

        String result = XmlSchemaGenerator.generateFormatInstructions(WithArray.class);

        assertThat(result).contains("Repeat for each item");
    }

    @Test
    void should_handle_set_collections() {
        class WithSet {
            public Set<String> uniqueTags;
        }

        String result = XmlSchemaGenerator.generateFormatInstructions(WithSet.class);

        assertThat(result).contains("<unique-tags>").contains("Repeat for each item");
    }
}
