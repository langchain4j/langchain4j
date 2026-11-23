package dev.langchain4j.output.parser.xml;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when XML output parsing fails.
 *
 * <p>This exception is thrown when:
 * <ul>
 *   <li>No valid XML can be extracted from the input text</li>
 *   <li>The extracted XML cannot be deserialized to the target type</li>
 *   <li>The input text is null or blank</li>
 * </ul>
 */
public class XmlOutputParsingException extends LangChain4jException {

    /**
     * Creates a new XmlOutputParsingException with the specified message.
     *
     * @param message the error message
     */
    public XmlOutputParsingException(String message) {
        super(message);
    }

    /**
     * Creates a new XmlOutputParsingException with the specified message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public XmlOutputParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
