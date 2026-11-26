package dev.langchain4j.service.output;

import static dev.langchain4j.internal.JsonParsingUtils.extractAndParseJson;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.service.output.ParsingUtils.outputParsingException;

import dev.langchain4j.Internal;
import java.util.Map;

@Internal
class MapOutputParser implements OutputParser<Map<String, Object>> {

    static final String FORMAT_INSTRUCTIONS =
            "\nYou must answer strictly in the following JSON format: {\"key\": (value)}";

    @Override
    public Map<String, Object> parse(String text) {
        if (isNullOrBlank(text)) {
            throw outputParsingException(text, Map.class);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = extractAndParseJson(text, Map.class).value();
            if (map == null) {
                throw outputParsingException(text, Map.class);
            }
            return map;
        } catch (OutputParsingException e) {
            throw e;
        } catch (Exception e) {
            throw outputParsingException(text, Map.class.getTypeName(), e);
        }
    }

    @Override
    public String formatInstructions() {
        return FORMAT_INSTRUCTIONS;
    }
}
