package dev.langchain4j.output.parser.xml;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.util.List;

/**
 * Parses XML output into a List of objects.
 *
 * <p>This parser handles the common case where an LLM returns multiple items
 * wrapped in a container XML element. XML requires a root element, so multiple
 * items must be wrapped in a container element.
 *
 * <p>Example usage:
 * <pre>{@code
 * XmlListOutputParser<Person> parser = new XmlListOutputParser<>(Person.class);
 *
 * // Get format instructions to include in prompt
 * String instructions = parser.formatInstructions();
 *
 * // Parse LLM response
 * String llmOutput = "<persons><item><name>John</name></item><item><name>Jane</name></item></persons>";
 * List<Person> people = parser.parse(llmOutput);
 * }</pre>
 *
 * @param <T> the element type
 */
public class XmlListOutputParser<T> {

    private final Class<T> elementType;
    private final JacksonXmlCodec xmlCodec;

    /**
     * Creates a new XmlListOutputParser for the given element type using default settings.
     *
     * @param elementType the class of list elements
     */
    public XmlListOutputParser(Class<T> elementType) {
        this(elementType, null);
    }

    /**
     * Creates a new XmlListOutputParser with a custom XmlMapper.
     *
     * @param elementType the class of list elements
     * @param xmlMapper custom XmlMapper, or null for defaults
     */
    public XmlListOutputParser(Class<T> elementType, XmlMapper xmlMapper) {
        this.elementType = elementType;
        this.xmlCodec = xmlMapper != null ? new JacksonXmlCodec(xmlMapper) : new JacksonXmlCodec();
    }

    /**
     * Parse the given text containing XML into a List of the target type.
     *
     * @param text the text containing XML
     * @return the parsed list
     * @throws XmlOutputParsingException if parsing fails
     */
    public List<T> parse(String text) {
        if (text == null || text.isBlank()) {
            throw new XmlOutputParsingException(
                    "Cannot parse null or blank text to List<" + elementType.getSimpleName() + ">");
        }

        try {
            return XmlParsingUtils.extractAndParseXml(text, this::parseXmlToList)
                    .value();
        } catch (XmlOutputParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new XmlOutputParsingException(
                    "Failed to parse XML to List<" + elementType.getSimpleName() + ">: " + e.getMessage(), e);
        }
    }

    private List<T> parseXmlToList(String xml) {
        return xmlCodec.fromXmlToList(xml, elementType);
    }

    /**
     * Returns format instructions describing the expected XML structure for a list.
     *
     * @return format instructions for the LLM
     */
    public String formatInstructions() {
        String elementName = toXmlElementName(elementType.getSimpleName());
        String containerName = elementName + "s";
        return XmlSchemaGenerator.generateCollectionFormatInstructions(elementType, containerName, elementName);
    }

    /**
     * Returns the element type this parser produces.
     *
     * @return the element class
     */
    public Class<T> elementType() {
        return elementType;
    }

    private static String toXmlElementName(String javaName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < javaName.length(); i++) {
            char c = javaName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append("-");
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
