package dev.langchain4j.chain;

import dev.langchain4j.service.AiServices;

/**
 * Represents a chain step that takes an input and produces an output.
 *
 * @param <Input>  the input type
 * @param <Output> the output type
 * @deprecated Please use {@link AiServices} instead.
 */
@Deprecated
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
