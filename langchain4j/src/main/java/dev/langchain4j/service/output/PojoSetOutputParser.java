package dev.langchain4j.service.output;

import dev.langchain4j.Internal;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

@Internal
class PojoSetOutputParser<T> extends PojoCollectionOutputParser<T, Set<T>> {

    PojoSetOutputParser(Class<T> type) {
        super(type);
    }

    @Override
    Supplier<Set<T>> emptyCollectionSupplier() {
        return LinkedHashSet::new;
    }

    @Override
    Class<?> collectionType() {
        return Set.class;
    }
}
