package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.JsonSchemaElementUtils.VisitedClassMetadata;
import dev.langchain4j.internal.PolymorphicTypes;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.JsonSchemaElementUtils.jsonObjectOrReferenceSchemaFrom;
import static dev.langchain4j.internal.JsonSchemaElementUtils.polymorphicSchemaFrom;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

@Internal
abstract class PojoCollectionOutputParser<T, CT extends Collection<T>> implements OutputParser<CT> {

    private final Class<T> type;
    private final PojoOutputParser<T> parser;

    PojoCollectionOutputParser(Class<T> type) {
        this.type = ensureNotNull(type, "type");
        this.parser = new PojoOutputParser<>(type);
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
        Map<Class<?>, VisitedClassMetadata> visited = new LinkedHashMap<>();
        JsonSchemaElement itemSchema;
        if (PolymorphicTypes.isPolymorphic(type)) {
            itemSchema = PojoOutputParser.referenceIfRecursive(
                    polymorphicSchemaFrom(type, null, false, visited), type, visited);
        } else {
            itemSchema = jsonObjectOrReferenceSchemaFrom(type, null, false, visited, true);
        }
        JsonArraySchema valuesArray = JsonArraySchema.builder().items(itemSchema).build();
        JsonObjectSchema rootElement = PolymorphicTypes.isPolymorphic(type)
                ? PojoOutputParser.wrapPolymorphic("values", valuesArray, visited)
                : JsonObjectSchema.builder()
                        .addProperty("values", valuesArray)
                        .required("values")
                        .build();
        JsonSchema jsonSchema = JsonSchema.builder()
                .name(collectionType().getSimpleName() + "_of_" + type.getSimpleName())
                .rootElement(rootElement)
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        throw new IllegalStateException();
    }
}
