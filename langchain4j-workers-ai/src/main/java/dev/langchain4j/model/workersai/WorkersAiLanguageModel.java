package dev.langchain4j.model.workersai;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workersai.client.AbstractWorkersAIModel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * WorkerAI Language model.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
@Slf4j
public class WorkersAiLanguageModel extends AbstractWorkersAIModel implements LanguageModel {

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkersAiLanguageModel(Builder builder) {
        super(builder);
    }

    /** {@inheritDoc} */
    @Override
    public Response<String> generate(String prompt) {
        try {
            retrofit2.Response<dev.langchain4j.model.workersai.client.WorkersAiTextCompletionResponse> retrofitResponse = workerAiClient
                    .generateText(new dev.langchain4j.model.workersai.client.WorkersAiTextCompletionRequest(prompt), accountIdentifier, modelName)
                    .execute();
            processErrors(retrofitResponse.body(), retrofitResponse.errorBody());
            if (retrofitResponse.body() == null) {
                throw new RuntimeException("Empty response");
            }
            return new Response<>(retrofitResponse.body().getResult().getResponse());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Response<String> generate(Prompt prompt) {
        return generate(prompt.text());
    }
}