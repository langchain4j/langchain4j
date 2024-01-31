package dev.langchain4j.chain;

/**
 * Represents a chain step that takes an input and produces an output.
 * It is recommended to use {@code AiServices} from {@code dev.langchain4j:langchain4j} module instead,
 * as it is more powerful.
 *
 * @param <Input>  the input type
 * @param <Output> the output type
 */
@FunctionalInterface
public interface Chain<Input, Output> {

    /**
     * Execute the chain step.
     *
     * @param input the input
     * @return the output
     */
    Output execute(Input input);
}
