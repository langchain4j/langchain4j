package dev.langchain4j.model.output;

import dev.langchain4j.service.TokenStream;

import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

/**
 * Parser that simply returns null output instructions.
 * The parse method never gets called.
 */
public class TokenStreamOutputParser implements OutputParser<TokenStream> {
    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(TokenStream.class);
    }

    @Override
    public TokenStream parse(final OutputParsingContext context) {
        throw new UnsupportedOperationException("TokenStreamOutputParser parse method should not be called");
    }

    @Override
    public String formatInstructions() {
        return null;
    }
}
