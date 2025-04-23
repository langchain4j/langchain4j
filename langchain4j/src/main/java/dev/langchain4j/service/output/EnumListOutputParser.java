package dev.langchain4j.service.output;

import dev.langchain4j.Internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Internal
class EnumListOutputParser<E extends Enum<E>> extends EnumCollectionOutputParser<E, List<E>> {

    EnumListOutputParser(Class<E> enumClass) {
        super(enumClass);
    }

    @Override
    Supplier<List<E>> emptyCollectionSupplier() {
        return ArrayList::new;
    }

    @Override
    Class<?> collectionType() {
        return List.class;
    }
}
