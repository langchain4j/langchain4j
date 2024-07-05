package dev.langchain4j.model.output;

public class DoubleOutputParser implements OutputParser<Double> {

    @Override
    public Double parse(String string) {
        return Double.parseDouble(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "floating point number";
    }
}
