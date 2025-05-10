package dev.langchain4j.spi.services;

import dev.langchain4j.Internal;
import dev.langchain4j.service.TokenStream;

import java.lang.reflect.Type;

@Internal
public interface TokenStreamAdapter {

    boolean canAdaptTokenStreamTo(Type type);

    Object adapt(TokenStream tokenStream);
}
