package dev.langchain4j.service;

import dev.langchain4j.model.output.*;

import java.util.*;


public class OutputParserResolver {

    private final Map<Class<?>, OutputParser<?>> parsers;

    public OutputParserResolver(final Map<Class<?>, OutputParser<?>> parsers) {
        this.parsers = Collections.unmodifiableMap(parsers);
    }

    public OutputParserResolver() {
        this(new ParsersBuilder().defaultParsers().build());
    }

    public <T> OutputParser<T> resolve(Class<T> returnType) {
        OutputParser<T> outputParser = (OutputParser<T>) parsers.get(returnType);
        if (outputParser != null) {
            return outputParser;
        }
        return getDefaultParser(returnType);
    }

    protected  <T> OutputParser<T> getDefaultParser(Class<T> returnType)
    {
        return new JsonOutputParser<>(returnType);
    }

}
