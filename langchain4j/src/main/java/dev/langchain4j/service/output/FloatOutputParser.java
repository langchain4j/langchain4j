package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsValueOrJson;

class FloatOutputParser implements OutputParser<Float> {

    @Override
    public Float parse(String text) {
        return parseAsValueOrJson(text, Float::parseFloat, Float.class);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
