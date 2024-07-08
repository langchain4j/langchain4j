package dev.langchain4j.service.output;

class FloatOutputParser implements OutputParser<Float> {

    @Override
    public Float parse(String string) {
        return Float.parseFloat(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
