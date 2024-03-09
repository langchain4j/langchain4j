package dev.langchain4j.model.workerai;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workerai.client.AbstractWorkerAIModel;
import dev.langchain4j.model.workerai.client.WorkerAiTextCompletionRequest;
import dev.langchain4j.model.workerai.client.WorkerAiTextCompletionResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * WorkerAI Language model.
 * <a href="https://developers.cloudflare.com/api/operations/workers-ai-post-run-model">...</a>
 */
@Slf4j
public class WorkerAiLanguageModel extends AbstractWorkerAIModel implements LanguageModel {

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkerAiLanguageModel(WorkerAiModelBuilder builder) {
        super(builder);
    }

    /** {@inheritDoc} */
    @Override
    public Response<String> generate(String prompt) {
        try {
            retrofit2.Response<WorkerAiTextCompletionResponse> retrofitResponse = workerAiClient
                    .generateText(new WorkerAiTextCompletionRequest(prompt), accountIdentifier, modelName)
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