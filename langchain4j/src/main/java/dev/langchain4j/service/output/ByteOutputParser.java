package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsStringOrJson;
import static dev.langchain4j.service.tool.DefaultToolExecutor.getBoundedLongValue;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.Optional;

@Internal
class ByteOutputParser implements OutputParser<Byte> {

    @Override
    public Byte parse(String text) {
        return parseAsStringOrJson(text, ByteOutputParser::parseByte, Byte.class);
    }

    private static Byte parseByte(String text) {
        try {
            return Byte.parseByte(text);
        } catch (NumberFormatException nfe) {
            return (byte) getBoundedLongValue(text, "byte", Byte.class, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
    }

    @Override
    public Optional<JsonSchema> jsonSchema() {
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("integer")
                .rootElement(JsonObjectSchema.builder()
                        .addIntegerProperty("value")
                        .required("value")
                        .build())
                .build();
        return Optional.of(jsonSchema);
    }

    @Override
    public String formatInstructions() {
        return "integer number in range [-128, 127]";
    }
}
