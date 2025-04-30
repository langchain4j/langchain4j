package dev.langchain4j.service.output;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Optional;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;

@Internal
class FloatOutputParser implements OutputParser<Float> {

    @Override
    public Float parse(String text) {
        return parseAsStringOrJson(text, Float::parseFloat, Float.class);
    }

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("number")
                .rootElement(JsonObjectSchema.builder()
                        .addNumberProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
