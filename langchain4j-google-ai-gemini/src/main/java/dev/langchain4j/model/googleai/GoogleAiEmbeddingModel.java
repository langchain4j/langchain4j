package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import retrofit2.Call;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

@Experimental
@Slf4j
public class GoogleAiEmbeddingModel implements EmbeddingModel {
    private static final int MAX_NUMBER_OF_SEGMENTS_PER_BATCH = 100;

    private final GeminiService geminiService;

    private final Gson GSON = new Gson();

    private final String modelName;
    private final String apiKey;
    private final Integer maxRetries;
    private final GoogleAiEmbeddingTaskType taskType;
    private final String titleMetadataKey;
    private final Integer outputDimensionality;

    @Builder
    public GoogleAiEmbeddingModel(
        String modelName,
        String apiKey,
        Integer maxRetries,
        GoogleAiEmbeddingTaskType taskType,
        String titleMetadataKey,
        Integer outputDimensionality,
        Duration timeout,
        Boolean logRequestsAndResponses) {

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.apiKey = ensureNotBlank(apiKey, "apiKey");

        this.maxRetries = getOrDefault(maxRetries, 3);

        this.taskType = taskType;
        this.titleMetadataKey = titleMetadataKey;

        this.outputDimensionality = outputDimensionality;

        Duration timeout1 = getOrDefault(timeout, Duration.ofSeconds(60));

        boolean logRequestsAndResponses1 = logRequestsAndResponses != null && logRequestsAndResponses;

        this.geminiService = GeminiService.getGeminiService(logRequestsAndResponses1 ? log : null, timeout1);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        GoogleAiEmbeddingRequest embeddingRequest = getGoogleAiEmbeddingRequest(textSegment);

        Call<GoogleAiEmbeddingResponse> geminiEmbeddingResponseCall =
            withRetry(() -> this.geminiService.embed(this.modelName, this.apiKey, embeddingRequest), this.maxRetries);

        GoogleAiEmbeddingResponse geminiResponse;
        try {
            retrofit2.Response<GoogleAiEmbeddingResponse> executed = geminiEmbeddingResponseCall.execute();
            geminiResponse = executed.body();

            if (executed.code() >= 300) {
                try (ResponseBody errorBody = executed.errorBody()) {
                    GeminiError error = GSON.fromJson(errorBody.string(), GeminiErrorContainer.class).getError();

                    throw new RuntimeException(
                        String.format("%s (code %d) %s", error.getStatus(), error.getCode(), error.getMessage()));
                }
            }
        } catch (IOException e) {

            throw new RuntimeException("An error occurred when calling the Gemini API endpoint (embed).", e);
        }

        if (geminiResponse != null) {
            return Response.from(Embedding.from(geminiResponse.getEmbedding().getValues()));
        } else {
            throw new RuntimeException("Gemini embedding response was null (embed)");
        }
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<GoogleAiEmbeddingRequest> embeddingRequests = textSegments.stream()
            .map(this::getGoogleAiEmbeddingRequest)
            .collect(Collectors.toList());

        List<Embedding> allEmbeddings = new ArrayList<>();
        int numberOfEmbeddings = embeddingRequests.size();
        int numberOfBatches = 1 + numberOfEmbeddings / MAX_NUMBER_OF_SEGMENTS_PER_BATCH;

        for (int i = 0; i < numberOfBatches; i++) {
            int startIndex = MAX_NUMBER_OF_SEGMENTS_PER_BATCH * i;
            int lastIndex = Math.min(startIndex + MAX_NUMBER_OF_SEGMENTS_PER_BATCH, numberOfEmbeddings);

            if (startIndex >= numberOfEmbeddings) break;

            GoogleAiBatchEmbeddingRequest batchEmbeddingRequest = new GoogleAiBatchEmbeddingRequest();
            batchEmbeddingRequest.setRequests(embeddingRequests.subList(startIndex, lastIndex));

            Call<GoogleAiBatchEmbeddingResponse> geminiBatchEmbeddingResponseCall =
                withRetry(() -> this.geminiService.batchEmbed(this.modelName, this.apiKey, batchEmbeddingRequest));

            GoogleAiBatchEmbeddingResponse geminiResponse;
            try {
                retrofit2.Response<GoogleAiBatchEmbeddingResponse> executed = geminiBatchEmbeddingResponseCall.execute();
                geminiResponse = executed.body();

                if (executed.code() >= 300) {
                    try (ResponseBody errorBody = executed.errorBody()) {
                        GeminiError error = GSON.fromJson(errorBody.string(), GeminiErrorContainer.class).getError();

                        throw new RuntimeException(
                            String.format("%s (code %d) %s", error.getStatus(), error.getCode(), error.getMessage()));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("An error occurred when calling the Gemini API endpoint (embedAll).", e);
            }

            if (geminiResponse != null) {
                allEmbeddings.addAll(geminiResponse.getEmbeddings().stream()
                    .map(values -> Embedding.from(values.getValues()))
                    .collect(Collectors.toList()));
            } else {
                throw new RuntimeException("Gemini embedding response was null (embedAll)");
            }
        }

        return Response.from(allEmbeddings);
    }

    private GoogleAiEmbeddingRequest getGoogleAiEmbeddingRequest(TextSegment textSegment) {
        GeminiPart geminiPart = GeminiPart.builder()
            .text(textSegment.text())
            .build();

        GeminiContent content = new GeminiContent(Collections.singletonList(geminiPart), null);

        GoogleAiEmbeddingTaskType taskType = null;
        if (textSegment.metadata() != null && textSegment.metadata().getString("taskType") != null) {
            taskType = GoogleAiEmbeddingTaskType.valueOf(textSegment.metadata().getString("taskType"));
        } else if (this.taskType != null) {
            taskType = this.taskType;
        }

        String title;
        if (textSegment.metadata() != null && textSegment.metadata().getString("title") != null) {
            title = textSegment.metadata().getString("title");
        } else {
            title = this.titleMetadataKey;
        }

        Integer dimension = getOrDefault(textSegment.metadata().getInteger("outputDimensionality"), this.dimension());

        return new GoogleAiEmbeddingRequest(
                "models/" + this.modelName,
                content,
                taskType,
                title,
                dimension
            );
    }

    @Override
    public int dimension() {
        return getOrDefault(this.outputDimensionality, 768);
    }
}
