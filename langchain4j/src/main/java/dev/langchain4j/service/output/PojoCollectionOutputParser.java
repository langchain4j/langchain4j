package dev.langchain4j.service.output;

import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Supplier;

@Internal
abstract class PojoCollectionOutputParser<T, CT extends Collection<T>> implements OutputParser<CT> {

    private final Class<T> type;
    private final OutputParser<T> parser;

    PojoCollectionOutputParser(Class<T> type, OutputParser<T> parser) {
        this.type = ensureNotNull(type, "type");
        this.parser = ensureNotNull(parser, "parser");
    }

    @Override
    public CT parse(String text) {
        return parseAsStringOrJson(text, parser::parse, emptyCollectionSupplier(), type());
    }

    abstract Supplier<CT> emptyCollectionSupplier();

    private String type() {
        return collectionType().getName() + "<" + type.getName() + ">";
    }

    abstract Class<?> collectionType();

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchemaElement itemSchema = parser.jsonSchema()
                .map(JsonSchema::rootElement)
                .orElseGet(() -> jsonObjectOrReferenceSchemaFrom(type, null, false, new LinkedHashMap<>(), true));

        JsonSchema jsonSchema = JsonSchema.builder()
                .name(collectionType().getSimpleName() + "_of_" + type.getSimpleName())
                .rootElement(JsonObjectSchema.builder()
                        .addProperty(
                                "values",
                                JsonArraySchema.builder().items(itemSchema).build())
                        .required("values")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        throw new IllegalStateException();
    }
}
