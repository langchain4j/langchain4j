package dev.langchain4j.output.parser.xml;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses XML output into a Set of objects.
 *
 * <p>This parser handles the common case where an LLM returns multiple unique items
 * wrapped in a container XML element. Duplicate items are automatically removed.
 *
 * <p>Example usage:
 * <pre>{@code
 * XmlSetOutputParser<Tag> parser = new XmlSetOutputParser<>(Tag.class);
 *
 * // Get format instructions to include in prompt
 * String instructions = parser.formatInstructions();
 *
 * // Parse LLM response (duplicates will be removed)
 * String llmOutput = "<tags><item><name>java</name></item><item><name>xml</name></item></tags>";
 * Set<Tag> tags = parser.parse(llmOutput);
 * }</pre>
 *
 * @param <T> the element type
 */
public class XmlSetOutputParser<T> {

    private final Class<T> elementType;
    private final JacksonXmlCodec xmlCodec;

    /**
     * Creates a new XmlSetOutputParser for the given element type using default settings.
     *
     * @param elementType the class of set elements
     */
    public XmlSetOutputParser(Class<T> elementType) {
        this(elementType, null);
    }

    /**
     * Creates a new XmlSetOutputParser with a custom XmlMapper.
     *
     * @param elementType the class of set elements
     * @param xmlMapper custom XmlMapper, or null for defaults
     */
    public XmlSetOutputParser(Class<T> elementType, XmlMapper xmlMapper) {
        this.elementType = elementType;
        this.xmlCodec = xmlMapper != null ? new JacksonXmlCodec(xmlMapper) : new JacksonXmlCodec();
    }

    /**
     * Parse the given text containing XML into a Set of the target type.
     *
     * @param text the text containing XML
     * @return the parsed set
     * @throws XmlOutputParsingException if parsing fails
     */
    public Set<T> parse(String text) {
        if (text == null || text.isBlank()) {
            throw new XmlOutputParsingException(
                    "Cannot parse null or blank text to Set<" + elementType.getSimpleName() + ">");
        }

        try {
            return XmlParsingUtils.extractAndParseXml(text, this::parseXmlToSet).value();
        } catch (XmlOutputParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new XmlOutputParsingException(
                    "Failed to parse XML to Set<" + elementType.getSimpleName() + ">: " + e.getMessage(), e);
        }
    }

    private Set<T> parseXmlToSet(String xml) {
        // Parse as List first, then convert to Set (preserving insertion order with LinkedHashSet)
        List<T> list = xmlCodec.fromXmlToList(xml, elementType);
        return new LinkedHashSet<>(list);
    }

    /**
     * Returns format instructions describing the expected XML structure for a set.
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
