/**
 * XML structured output parsing for LangChain4j.
 *
 * <p>This package provides XML parsing capabilities for LLM outputs:
 * <ul>
 *   <li>{@link dev.langchain4j.output.parser.xml.XmlOutputParser} - Parse XML to single POJO</li>
 *   <li>{@link dev.langchain4j.output.parser.xml.XmlListOutputParser} - Parse XML to List</li>
 *   <li>{@link dev.langchain4j.output.parser.xml.XmlSetOutputParser} - Parse XML to Set</li>
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
 * Person person = parser.parse(llmOutput);
 * }</pre>
 *
 * <p>The parsers support Jackson XML annotations for customizing XML structure:
 * <ul>
 *   <li>{@code @JacksonXmlRootElement} - Custom root element name</li>
 *   <li>{@code @JacksonXmlProperty} - Custom element name, attribute mode</li>
 *   <li>{@code @JacksonXmlElementWrapper} - Custom wrapper for collections</li>
 *   <li>{@code @JacksonXmlCData} - Wrap content in CDATA section</li>
 * </ul>
 *
 * <p>The parsers also support langchain4j's {@code @Description} annotation
 * for adding documentation to format instructions.
 *
 * @see dev.langchain4j.output.parser.xml.XmlOutputParser
 * @see dev.langchain4j.output.parser.xml.XmlListOutputParser
 * @see dev.langchain4j.output.parser.xml.XmlSetOutputParser
 */
package dev.langchain4j.output.parser.xml;
