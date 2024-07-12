package dev.langchain4j.model.output;

public class BooleanOutputParser implements OutputParser<Boolean> {

    @Override
    public Boolean parse(String string) {
        return Boolean.parseBoolean(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "one of [true, false]";
    }
}
