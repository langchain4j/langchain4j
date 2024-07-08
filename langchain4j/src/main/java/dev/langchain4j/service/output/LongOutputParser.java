package dev.langchain4j.service.output;

class LongOutputParser implements OutputParser<Long> {

    @Override
    public Long parse(String string) {
        return Long.parseLong(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
