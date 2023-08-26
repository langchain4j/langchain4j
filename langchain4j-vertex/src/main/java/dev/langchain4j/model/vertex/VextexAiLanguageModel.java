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
 * Represents a connection to the Vertex LLM with a completion interface, such as text-davinci-003.
 * However, it's recommended to use {@link VertexAiChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 */
public class VextexAiLanguageModel implements LanguageModel
//        , TokenCountEstimator
{

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
        this.vertexAiParameters = VertexAiParameters.builder()
                .temperature(temperature)
                .maxOutputTokens(maxOutputTokens)
                .topK(topK)
                .topP(topP)
                .build();
    }


    @Override
    public String process(String text) {

        try {
            PredictionServiceSettings predictionServiceSettings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build();

            PredictionServiceClient predictionServiceClient = PredictionServiceClient.create(predictionServiceSettings);

            final EndpointName endpointName =
                    EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, "text-bison@001");

            VertexAiCompletionInstance vertexAiCompletionInstance = VertexAiCompletionInstance.builder()
                    .prompt(text)
                    .build();

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
                .get("candidates")
                .getListValue()
                .getValues(0)
                .getStructValue()
                .getFieldsMap()
                .get("content")
                .getStringValue();
    }

    //TODO delete this
    public static void main(String[] args) {
        VextexAiLanguageModel vextexAiLanguageModel = new VextexAiLanguageModel("text-bison@001", "langchain4j", "us-central1", "google",
                "us-central1-aiplatform.googleapis.com:443", 0.2, 50, 40, 0.95); //TODO defaults

        String response = vextexAiLanguageModel.process("hi, what is java?");
        System.out.println(response);
    }

//
//    @Override
//    public int estimateTokenCount(String prompt) {
//        return tokenizer.estimateTokenCountInText(prompt);
//    }

}
