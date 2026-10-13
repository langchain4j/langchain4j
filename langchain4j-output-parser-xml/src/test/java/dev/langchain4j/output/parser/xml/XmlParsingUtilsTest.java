package dev.langchain4j.output.parser.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class XmlParsingUtilsTest {

    // A parser function that only accepts valid XML (starts with <)
    private static final XmlParsingUtils.ThrowingFunction<String, String> XML_ONLY_PARSER = input -> {
        if (input.trim().startsWith("<") && input.trim().endsWith(">")) {
            return input;
        }
        throw new RuntimeException("Not valid XML");
    };

    @Test
    void should_parse_direct_xml() throws Exception {
        String xml = "<root><value>test</value></root>";

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(xml, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo(xml);
        assertThat(result.xml()).isEqualTo(xml);
    }

    @Test
    void should_extract_from_markdown_code_block() throws Exception {
        String text =
                """
                Here is the XML:
                ```xml
                <data>content</data>
                ```
                End of response.
                """;

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo("<data>content</data>");
    }

    @Test
    void should_extract_from_markdown_without_language_tag() throws Exception {
        String text =
                """
                ```
                <data>content</data>
                ```
                """;

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo("<data>content</data>");
    }

    @Test
    void should_extract_xml_from_mixed_text() throws Exception {
        String text =
                """
                Based on my analysis, here is the result:
                <person><name>John</name><age>30</age></person>
                I hope this helps with your request.
                """;

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo("<person><name>John</name><age>30</age></person>");
    }

    @Test
    void should_extract_multiline_xml_from_text() throws Exception {
        String text =
                """
                Here is the data:
                <order>
                    <id>123</id>
                    <total>99.99</total>
                </order>
                Thanks!
                """;

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.xml()).contains("<order>");
        assertThat(result.xml()).contains("</order>");
    }

    @Test
    void should_handle_self_closing_tags() throws Exception {
        String text = "Here is an empty element: <empty/> and that's it.";

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo("<empty/>");
    }

    @Test
    void should_handle_self_closing_with_attributes() throws Exception {
        String text = "Result: <item id=\"123\" status=\"active\"/> done.";

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).contains("item");
        assertThat(result.value()).contains("id=\"123\"");
    }

    @Test
    void should_prefer_first_valid_xml_when_multiple_found() throws Exception {
        String text =
                """
                First XML: <first>one</first>
                Second XML: <second>two</second>
                """;

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo("<first>one</first>");
    }

    @Test
    void should_throw_when_no_xml_found() {
        String text = "This is just plain text without any XML content.";

        assertThatThrownBy(() -> XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER))
                .isInstanceOf(XmlOutputParsingException.class)
                .hasMessageContaining("No valid XML found");
    }

    @Test
    void should_propagate_parsing_exception() {
        String text = "<data>content</data>";

        assertThatThrownBy(() -> XmlParsingUtils.extractAndParseXml(text, input -> {
                    throw new RuntimeException("Parsing failed");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Parsing failed");
    }

    @Test
    void should_try_next_xml_when_first_fails_parsing() throws Exception {
        String text =
                """
                <invalid>not parseable by our function</invalid>
                <valid>this one works</valid>
                """;

        // Function that only accepts "valid" content
        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, input -> {
            if (input.contains("invalid")) {
                throw new RuntimeException("Cannot parse invalid");
            }
            return input;
        });

        assertThat(result.value()).isEqualTo("<valid>this one works</valid>");
    }

    @Test
    void should_handle_xml_with_attributes() throws Exception {
        String text = "Data: <person name=\"John\" age=\"30\">content</person>";

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.value()).contains("name=\"John\"");
        assertThat(result.value()).contains("age=\"30\"");
    }

    @Test
    void should_handle_nested_xml() throws Exception {
        String text =
                """
                Result:
                <root>
                    <child>
                        <grandchild>value</grandchild>
                    </child>
                </root>
                """;

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(text, XML_ONLY_PARSER);

        assertThat(result.xml()).contains("<root>");
        assertThat(result.xml()).contains("<grandchild>");
        assertThat(result.xml()).contains("</root>");
    }

    @Test
    void should_trim_whitespace_from_direct_xml() throws Exception {
        String xml = "   <root>value</root>   ";

        XmlParsingUtils.ParsedXml<String> result = XmlParsingUtils.extractAndParseXml(xml, XML_ONLY_PARSER);

        assertThat(result.value()).isEqualTo("<root>value</root>");
    }
}
