package dev.langchain4j.service.output;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

class PojoListOutputParser<T> implements OutputParser<List<T>> {

    private final Class<T> type;
    private final PojoOutputParser<T> parser;

    PojoListOutputParser(Class<T> type) {
        this.type = ensureNotNull(type, "type");
        this.parser = new PojoOutputParser<>(type);
    }

    @Override
    public List<T> parse(String text) {
        return (List<T>) parseCollectionAsValueOrJson(text, parser::parse, ArrayList::new, getType());
    }

    private String getType() {
        return "java.util.List<" + type.getName() + ">";
    }

    @Override
    public String formatInstructions() {
        throw new IllegalStateException();
    }
}
