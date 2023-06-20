package dev.langchain4j.model.output;

public interface OutputParser<T> {

    T parse(String text);

    String formatInstructions();
}
