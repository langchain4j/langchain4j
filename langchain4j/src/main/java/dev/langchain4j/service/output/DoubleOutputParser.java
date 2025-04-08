package dev.langchain4j.service.output;

class DoubleOutputParser implements OutputParser<Double> {

    @Override
    public Double parse(String text) {
        return ParsingUtils.parseAsValueOrJson(text, Double::parseDouble, Double.class);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
