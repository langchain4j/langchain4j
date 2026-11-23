package dev.langchain4j.output.parser.xml;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Parses LLM text output containing XML into a strongly-typed Java object.
 *
 * <p>This parser:
 * <ul>
 *   <li>Provides format instructions to guide the LLM</li>
 *   <li>Extracts XML from potentially mixed text/XML output</li>
 *   <li>Deserializes XML to the target type using Jackson</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);
 *
 * // Get format instructions to include in prompt
 * String instructions = parser.formatInstructions();
 *
 * // Parse LLM response
 * String llmOutput = "<person><name>John</name><age>30</age></person>";
 * Person person = parser.parse(llmOutput);
 * }</pre>
 *
 * <p>For custom XmlMapper (e.g., custom date formats):
 * <pre>{@code
 * XmlMapper customMapper = XmlMapper.builder()
 *     .addModule(new JavaTimeModule())
 *     .build();
 * XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class, customMapper);
 * }</pre>
 *
 * @param <T> the type to parse XML into
 */
public class XmlOutputParser<T> {

    private final Class<T> type;
    private final JacksonXmlCodec xmlCodec;

    /**
     * Creates a new XmlOutputParser for the given type using default settings.
     *
     * @param type the class to parse XML into
     */
    public XmlOutputParser(Class<T> type) {
        this(type, null);
    }

    /**
     * Creates a new XmlOutputParser with a custom XmlMapper.
     *
     * <p>Use this constructor when you need:
     * <ul>
     *   <li>Custom date/time formats for legacy system integration</li>
     *   <li>Additional Jackson modules (Kotlin, Joda-Time, etc.)</li>
     *   <li>Custom naming strategies</li>
     *   <li>Specific null handling behavior</li>
     * </ul>
     *
     * @param type the class to parse XML into
     * @param xmlMapper custom XmlMapper, or null for defaults
     */
    public XmlOutputParser(Class<T> type, XmlMapper xmlMapper) {
        this.type = type;
        this.xmlCodec = xmlMapper != null ? new JacksonXmlCodec(xmlMapper) : new JacksonXmlCodec();
    }

    /**
     * Parse the given text containing XML into the target type.
     *
     * <p>The parser will attempt to extract valid XML from the text,
     * handling cases where the LLM includes explanatory text around the XML.
     *
     * @param text the text containing XML
     * @return the parsed object
     * @throws XmlOutputParsingException if parsing fails
     */
    public T parse(String text) {
        if (text == null || text.isBlank()) {
            throw new XmlOutputParsingException("Cannot parse null or blank text to " + type.getSimpleName());
        }

        try {
            return XmlParsingUtils.extractAndParseXml(text, xml -> xmlCodec.fromXml(xml, type))
                    .value();
        } catch (XmlOutputParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new XmlOutputParsingException(
                    "Failed to parse XML to " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Returns format instructions describing the expected XML structure.
     *
     * <p>These instructions should be included in the prompt to guide
     * the LLM to produce correctly structured XML output.
     *
     * @return format instructions for the LLM
     */
    public String formatInstructions() {
        return XmlSchemaGenerator.generateFormatInstructions(type);
    }

    /**
     * Returns the target type this parser produces.
     *
     * @return the target class
     */
    public Class<T> targetType() {
        return type;
    }
}
