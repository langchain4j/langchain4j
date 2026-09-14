package dev.langchain4j.output.parser.xml;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Jackson-based XML codec for serialization and deserialization.
 */
final class JacksonXmlCodec {

    private final XmlMapper xmlMapper;

    /**
     * Creates a JacksonXmlCodec with default configuration.
     * <ul>
     *   <li>Unknown properties are ignored (lenient parsing)</li>
     *   <li>Output is indented for readability</li>
     * </ul>
     */
    JacksonXmlCodec() {
        this.xmlMapper = XmlMapper.builder()
                .addModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .build();
    }

    /**
     * Creates a JacksonXmlCodec with a custom XmlMapper.
     *
     * @param xmlMapper the custom XmlMapper to use
     */
    JacksonXmlCodec(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    public String toXml(Object object) {
        try {
            return xmlMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to XML", e);
        }
    }

    public <T> T fromXml(String xml, Class<T> type) {
        try {
            return xmlMapper.readValue(xml, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize from XML: " + e.getMessage(), e);
        }
    }

    public <T> T fromXml(String xml, Type type) {
        try {
            return xmlMapper.readValue(xml, xmlMapper.constructType(type));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize from XML: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialize XML string to a List of objects.
     *
     * @param xml the XML string
     * @param elementType the class of list elements
     * @param <T> the element type
     * @return the deserialized list
     */
    public <T> List<T> fromXmlToList(String xml, Class<T> elementType) {
        try {
            return xmlMapper.readValue(
                    xml, xmlMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize from XML: " + e.getMessage(), e);
        }
    }
}
