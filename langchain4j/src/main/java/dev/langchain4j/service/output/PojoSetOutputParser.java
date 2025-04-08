package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.Set;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

class PojoSetOutputParser<T> implements OutputParser<Set<T>> {

    private final Class<T> type;
    private final PojoOutputParser<T> parser;

    PojoSetOutputParser(Class<T> type) {
        this.type = ensureNotNull(type, "type");
        this.parser = new PojoOutputParser<>(type);
    }

    @Override
    public Set<T> parse(String text) {
        return (Set<T>) parseCollectionAsValueOrJson(text, parser::parse, LinkedHashSet::new, getType());
    }

    private String getType() {
        return "java.util.Set<" + type.getName() + ">";
    }

    @Override
    public String formatInstructions() {
        throw new IllegalStateException();
    }
}
