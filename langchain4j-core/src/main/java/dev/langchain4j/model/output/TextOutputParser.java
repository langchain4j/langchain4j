package dev.langchain4j.model.output;

/**
 * An output parser that parses text.
 * @param <T> the type of the output.
 */
public interface TextOutputParser<T> extends OutputParser<T> {
    /**
     * By default, parse the response content as text.
     * @param context the parsing context.
     * @return the parsed output.
     */
    @Override
    default T parse(final OutputParsingContext context) {
        return parse(context.getResponse().content().text());
    }

    /**
     * Parse the given text.
     *
     * @param text the text to parse.
     * @return the parsed output.
     */
    T parse(final String text);
}
