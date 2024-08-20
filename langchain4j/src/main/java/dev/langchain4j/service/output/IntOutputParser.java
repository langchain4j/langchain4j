package dev.langchain4j.service.output;

class IntOutputParser implements OutputParser<Integer> {

    @Override
    public Integer parse(String string) {
        return Integer.parseInt(string.trim());
    }

    @Override
    public String formatInstructions() {
        return "integer number";
    }
}
