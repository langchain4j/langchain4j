package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

class StringSetOutputParser extends StringCollectionOutputParser<Set<String>> {

    @Override
    Supplier<Set<String>> emptyCollectionSupplier() {
        return LinkedHashSet::new;
    }

    @Override
    Class<?> collectionType() {
        return Set.class;
    }
}
