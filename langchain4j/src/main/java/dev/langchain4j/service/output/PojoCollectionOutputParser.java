package dev.langchain4j.service.output;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

abstract class PojoCollectionOutputParser<T, CT extends Collection<T>> implements OutputParser<CT> {

    private final Class<T> type;
    private final PojoOutputParser<T> parser;

    PojoCollectionOutputParser(Class<T> type) {
        this.type = ensureNotNull(type, "type");
        this.parser = new PojoOutputParser<>(type);
    }

    @Override
    public CT parse(String text) {
        return parseCollectionAsValueOrJson(text, parser::parse, emptyCollectionSupplier(), getType());
    }

    abstract Supplier<CT> emptyCollectionSupplier();

    private String getType() {
        return collectionType().getName() + "<" + type.getName() + ">";
    }

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name(collectionType().getSimpleName() + "_of_" + type.getSimpleName())
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("items", JsonArraySchema.builder()
                                .items(jsonObjectOrReferenceSchemaFrom(type, null, false, new LinkedHashMap<>(), true))
                                .build())
                        .required("items")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    abstract Class<?> collectionType();

    @Override
    public String formatInstructions() {
        throw new IllegalStateException();
    }
}
