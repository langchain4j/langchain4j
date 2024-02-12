package dev.langchain4j.model.language;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;

/**
 * A {@link StreamingLanguageModel} which throws a {@link ModelDisabledException} for all of its methods
 * <p>
 *     This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledStreamingLanguageModel implements StreamingLanguageModel {
    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        throw new ModelDisabledException("StreamingLanguageModel is disabled");
    }

    @Override
    public void generate(Prompt prompt, StreamingResponseHandler<String> handler) {
        throw new ModelDisabledException("StreamingLanguageModel is disabled");
    }
}
