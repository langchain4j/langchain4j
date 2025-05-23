package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Cognisphere;
import java.util.function.Function;

public interface OutputtingService<T> {

    T outputName(String outputName);

    T output(Function<Cognisphere, Object> output);
}
