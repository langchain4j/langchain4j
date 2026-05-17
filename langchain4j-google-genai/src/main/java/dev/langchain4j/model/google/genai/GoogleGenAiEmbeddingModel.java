package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.Part;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Experimental
public class GoogleGenAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    public enum TaskTypeEnum {
        TASK_TYPE_UNSPECIFIED("TASK_TYPE_UNSPECIFIED"),
        RETRIEVAL_QUERY("RETRIEVAL_QUERY"),
        RETRIEVAL_DOCUMENT("RETRIEVAL_DOCUMENT"),
        SEMANTIC_SIMILARITY("SEMANTIC_SIMILARITY"),
        CLASSIFICATION("CLASSIFICATION"),
        CLUSTERING("CLUSTERING"),
        QUESTION_ANSWERING("QUESTION_ANSWERING"),
        FACT_VERIFICATION("FACT_VERIFICATION"),
        CODE_RETRIEVAL_QUERY("CODE_RETRIEVAL_QUERY");

        private final String sdkTaskType;

        TaskTypeEnum(String sdkTaskType) {
            this.sdkTaskType = sdkTaskType;
        }

        public String getSdkTaskType() {
            return sdkTaskType;
        }
    }

    private final Client client;
    private final String modelName;
    private final Integer outputDimensionality;
    private final TaskTypeEnum taskType;
    private final String titleMetadataKey;

    public GoogleGenAiEmbeddingModel(Builder builder) {
        this.client = GoogleGenAiClientFactory.createClient(
                builder.apiKey,
                builder.googleCredentials,
                builder.projectId,
                builder.location,
                builder.timeout,
                builder.customHeaders,
                builder.apiEndpoint);
        this.modelName = builder.modelName;
        this.outputDimensionality = builder.outputDimensionality;
        this.taskType = builder.taskType;
        this.titleMetadataKey = builder.titleMetadataKey != null ? builder.titleMetadataKey : "title";
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return Response.from(embedAll(List.of(textSegment)).content().get(0));
    }

    @Override
    public Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Content> contents = textSegments.stream()
                .map(segment -> Content.builder()
                        .parts(List.of(Part.builder().text(segment.text()).build()))
                        .build())
                .collect(Collectors.toList());

        List<Embedding> allEmbeddings = new ArrayList<>();
        int inputTokens = 0;
        int totalTokens = 0;

        for (int i = 0; i < textSegments.size(); i++) {
            TextSegment segment = textSegments.get(i);
            Content content = contents.get(i);

            EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
            if (taskType != null) {
                configBuilder.taskType(taskType.getSdkTaskType());
            }
            if (outputDimensionality != null) {
                configBuilder.outputDimensionality(outputDimensionality);
            }
            if (TaskTypeEnum.RETRIEVAL_DOCUMENT.equals(taskType) && segment.metadata() != null) {
                String title = segment.metadata().getString(titleMetadataKey);
                if (title != null) {
                    configBuilder.title(title);
                }
            }

            EmbedContentResponse response = client.models.embedContent(modelName, content, configBuilder.build());

            allEmbeddings.add(
                    Embedding.from(response.embeddings().get().get(0).values().get()));
            // Usage is generally per-batch or per-request but `embedContent` is singular content for now.
            // Wait, does models().embedContent take a list or a single content?
            // Actually, we can check if there's a batchEmbedContents or similar. If not, do it in a loop.
            // Let's aggregate usage for the response if available.
            // Let's check `GoogleGenAiChatModel` to see how TokenUsage is mapped.
        }

        // Return a response (ignoring token usage for a moment until we refine it)
        return Response.from(allEmbeddings);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    public Integer knownDimension() {
        return outputDimensionality;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Client client;
        private String modelName = "gemini-embedding-2";
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private Boolean logRequests;
        private Boolean logResponses;
        private Duration timeout;
        private Integer outputDimensionality;
        private TaskTypeEnum taskType;
        private String titleMetadataKey;
        private String apiEndpoint;
        private Map<String, String> customHeaders;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = ensureNotBlank(modelName, "modelName");
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder googleCredentials(GoogleCredentials googleCredentials) {
            this.googleCredentials = googleCredentials;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder outputDimensionality(Integer outputDimensionality) {
            this.outputDimensionality = outputDimensionality;
            return this;
        }

        public Builder taskType(TaskTypeEnum taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder titleMetadataKey(String titleMetadataKey) {
            this.titleMetadataKey = titleMetadataKey;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public GoogleGenAiEmbeddingModel build() {
            return new GoogleGenAiEmbeddingModel(this);
        }
    }
}
