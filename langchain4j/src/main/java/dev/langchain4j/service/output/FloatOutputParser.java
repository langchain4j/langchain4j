package dev.langchain4j.service.output;

class FloatOutputParser implements OutputParser<Float> {

    @Override
    public Float parse(String text) {
        return ParsingUtils.parseAsValueOrJson(text, Float::parseFloat, Float.class);
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
