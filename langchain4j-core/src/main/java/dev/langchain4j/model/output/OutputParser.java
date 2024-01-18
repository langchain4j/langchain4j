package dev.langchain4j.model.output;

/**
 * Represents an output parser.
 * @param <T> the type of the output.
 */
public interface OutputParser<T> {

    /**
     * Parse the given text.
     * @param text the text to parse.
     * @return the parsed output.
     */
    T parse(String text);

    /**
     * Description of the text format.
     * @return the description of the text format.
     */
    String formatInstructions();
}
