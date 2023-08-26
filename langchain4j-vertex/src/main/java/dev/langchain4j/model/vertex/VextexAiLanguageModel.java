package dev.langchain4j.model.vertex;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.model.language.LanguageModel;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.protobuf.Value.newBuilder;
import static dev.langchain4j.internal.Json.toJson;

/**
 * Represents a connection to the Vertex LLM with a completion interface, such as text-bison.
 * However, it's recommended to use {@link VertexAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 */
public class VextexAiLanguageModel implements LanguageModel {

    private final String modelName;
    private final String project;
    private final String location;
    private final String publisher;
    private final String endpoint;
    private final VertexAiParameters vertexAiParameters;

    VextexAiLanguageModel(String modelName,
                          String project,
                          String location,
                          String publisher,
                          String endpoint,
                          Double temperature,
                          Integer maxOutputTokens,
                          Integer topK,
                          Double topP) {

        this.modelName = modelName;
        this.project = project;
        this.location = location;
        this.publisher = publisher;
        this.endpoint = endpoint;
        this.vertexAiParameters = new VertexAiParameters(temperature, maxOutputTokens, topK, topP);
    }

    @Override
    public String process(String text) {

        try {
            PredictionServiceSettings predictionServiceSettings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build();

            PredictionServiceClient predictionServiceClient = PredictionServiceClient.create(predictionServiceSettings);

            final EndpointName endpointName = EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, modelName);

            VertexAiCompletionInstance vertexAiCompletionInstance = new VertexAiCompletionInstance(text);

            Value.Builder instanceValue = newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiCompletionInstance), instanceValue);
            List<Value> instances = Collections.singletonList(instanceValue.build());

            Value.Builder parameterValueBuilder = Value.newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiParameters), parameterValueBuilder);
            Value parameterValue = parameterValueBuilder.build();

            PredictResponse predictResponse = predictionServiceClient.predict(endpointName, instances, parameterValue);

            return extractContent(predictResponse);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractContent(PredictResponse predictResponse) {
        return predictResponse.getPredictions(0)
                .getStructValue()
                .getFieldsMap()
                .get("content")
                .getStringValue();
    }

}
