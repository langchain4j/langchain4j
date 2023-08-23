package dev.langchain4j.model.vertex;

import com.google.cloud.aiplatform.util.ValueConverter;
import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Json.toJson;
import static java.util.stream.Collectors.toList;

/**
 * Represents a connection to the Vertex embedding model, such as textembedding-gecko.
 */
public class VertexAiEmbeddingModel implements EmbeddingModel
//,TokenCountEstimator
{

    private final String modelName;
    private final String project;
    private final String location;
    private final String publisher;
    private final String endpoint;

    VertexAiEmbeddingModel(String modelName,
                           String project,
                           String location,
                           String publisher,
                           String endpoint) {
        this.modelName = modelName;
        this.project = project;
        this.location = location;
        this.publisher = publisher;
        this.endpoint = endpoint;
    }


    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private List<Embedding> embedTexts(List<String> texts) {
        try {
            PredictionServiceSettings predictionServiceSettings =
                    PredictionServiceSettings.newBuilder()
                            .setEndpoint(endpoint)
                            .build();

            try (PredictionServiceClient predictionServiceClient =
                         PredictionServiceClient.create(predictionServiceSettings)) {
                EndpointName endpointName =
                        EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, modelName);

                Value.Builder instanceValue = Value.newBuilder();
                texts.forEach(content -> {
                    try {
                        JsonFormat.parser().merge(toJson(new VertexEmbeddingInstance(content)), instanceValue);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });

                List<Value> instances = new ArrayList<>();
                instances.add(instanceValue.build());

                PredictResponse predictResponse =
                        predictionServiceClient.predict(endpointName, instances, ValueConverter.EMPTY_VALUE);

                List<Float> numberValuesList = predictResponse.getPredictions(0)
                        .getStructValue()
                        .getFieldsMap()
                        .get("embeddings")
                        .getStructValue()
                        .getFieldsOrThrow("values")
                        .getListValue()
                        .getValuesList()
                        .stream().map(v -> (float) v.getNumberValue())
                        .collect(toList());

                Embedding embedding = Embedding.from(numberValuesList);

                System.out.println(embedding);
                return Collections.singletonList(embedding);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


//        EmbeddingResponse response = withRetry(() -> client.embedding(request).execute(), maxRetries);
//        return response.data().stream()
//                .map(openAiEmbedding -> Embedding.from(openAiEmbedding.embedding()))
//                .collect(toList());
    }


//    @Override
//    public int estimateTokenCount(String text) {
//        return tokenizer.estimateTokenCountInText(text);
//    }
//
//    public static OpenAiEmbeddingModel withApiKey(String apiKey) {
//        return builder().apiKey(apiKey).build();
//    }
}
