package dev.langchain4j.service.output;

import static dev.langchain4j.service.output.ParsingUtils.parseAsValueOrJson;

class DoubleOutputParser implements OutputParser<Double> {

    @Override
    public Double parse(String text) {
        return parseAsValueOrJson(text, Double::parseDouble, Double.class);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
