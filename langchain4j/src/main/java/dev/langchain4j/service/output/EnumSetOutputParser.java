package dev.langchain4j.service.output;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

class EnumSetOutputParser<E extends Enum<E>> extends EnumCollectionOutputParser<E, Set<E>> {

    EnumSetOutputParser(Class<E> enumClass) {
        super(enumClass);
    }

    @Override
    Supplier<Set<E>> emptyCollectionSupplier() {
        return LinkedHashSet::new;
    }

    @Override
    Class<?> collectionType() {
        return Set.class;
    }
}
