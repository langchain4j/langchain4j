package dev.langchain4j.model.output;

import dev.langchain4j.data.message.AiMessage;
import lombok.Builder;

import java.lang.reflect.Method;
import java.util.Optional;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

/**
 * Parses the output of a service method.
 */
@Builder
public class ServiceOutputParser {
    /**
     * Provides the parser for the output type.
     */
    private final ParserProvider parserProvider;
    /**
     * The method for which the output is parsed.
     */
    private final Method method;

    /**
     * Creates a default parser for the given method that uses {@link DefaultParserProvider}.
     * @param method The method for which to create the parser.
     * @return The parser.
     */
    public static ServiceOutputParser createDefault(final Method method) {
        return create(DefaultParserProvider.create(), method);
    }

    /**
     * Creates a parser for the given method using the given provider.
     * @param provider The provider to use.
     * @param method The method for which to create the parser.
     * @return The parser.
     */
    public static ServiceOutputParser create(final ParserProvider provider, final Method method) {
        return builder()
                .parserProvider(provider)
                .method(method)
                .build();
    }

    /**
     * Parses the output of the method.
     * @param context The context to parse.
     * @return The parsed output.
     */
    public Object parse(final OutputParsingContext context) {
        if (Response.class == method.getReturnType()) {
            return context.getResponse();
        }
        final AiMessage message = context.getResponse().content();
        if (AiMessage.class == method.getReturnType()) {
            return message;
        }
        return parserProvider
                .getOrDefault(method)
                .parse(context);
    }

    /**
     * Formats the instructions for the output.
     * @return The instructions.
     */
    public String outputFormatInstructions() {
        if (void.class == method.getReturnType()) {
            throw illegalConfiguration("Return type of method=%s cannot be void", method.getName());
        }
        final OutputParser<?> parser = parserProvider.getOrDefault(method);
        final String instructions = parser.formatInstructions();
        final String postlude = parser.customFormatPostlude();
        final StringBuilder sb = new StringBuilder();
        if (instructions != null) {
            final String prelude = Optional.ofNullable(parser.customFormatPrelude()).orElse("\nYou must answer strictly in the following format: ");
            sb.append(prelude).append(instructions);
        }
        if (postlude != null) {
            sb.append(postlude);
        }
        return sb.toString();
    }
}
