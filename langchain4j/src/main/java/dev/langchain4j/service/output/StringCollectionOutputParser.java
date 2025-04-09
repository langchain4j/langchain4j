package dev.langchain4j.service.output;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.service.output.ParsingUtils.parseCollectionAsValueOrJson;

abstract class StringCollectionOutputParser<CT extends Collection<String>> implements OutputParser<CT> {

    @Override
    public CT parse(String text) {
        return parseCollectionAsValueOrJson(text, s -> s, emptyCollectionSupplier(), getType());
    }

    abstract Supplier<CT> emptyCollectionSupplier();

    private String getType() {
        return collectionType().getName() + "<java.lang.String>";
    }

    abstract Class<?> collectionType();

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name(collectionType().getSimpleName() + "_of_String")
                .rootElement(JsonObjectSchema.builder()
                        .addProperty("items", JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .build())
                        .required("items")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        return "\nYou must put every item on a separate line.";
    }
}
