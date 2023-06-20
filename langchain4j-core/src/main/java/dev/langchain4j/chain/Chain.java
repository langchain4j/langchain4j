package dev.langchain4j.chain;

public interface Chain<Input, Output> {

    Output execute(Input input);
}
