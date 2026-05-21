package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
    private final Integer maxSegmentsPerBatch;
    private final Integer maxRetries;

    public GoogleGenAiEmbeddingModel(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
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
        this.titleMetadataKey = getOrDefault(builder.titleMetadataKey, "title");
        this.maxSegmentsPerBatch = getOrDefault(builder.maxSegmentsPerBatch, 100);
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
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
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(new ArrayList<>());
        }

        // Group IndexedSegment objects by their segment title (or null key if none).
        // Rationale: In document ingestion pipelines, multiple text segments often share the same document title.
        // Since the google-genai SDK's embedContent method with list parameters shares a single EmbedContentConfig
        // (which only supports a single title), grouping segments by their title allows us to batch texts sharing
        // the same title together in one API request. This maximizes API throughput and fully preserves distinct
        // titles.
        Map<String, List<IndexedSegment>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < textSegments.size(); i++) {
            TextSegment segment = textSegments.get(i);
            String title = null;
            if (TaskTypeEnum.RETRIEVAL_DOCUMENT.equals(taskType) && segment.metadata() != null) {
                title = segment.metadata().getString(titleMetadataKey);
            }
            grouped.computeIfAbsent(title, k -> new ArrayList<>()).add(new IndexedSegment(i, segment));
        }

        Embedding[] embeddingsArray = new Embedding[textSegments.size()];

        for (Map.Entry<String, List<IndexedSegment>> entry : grouped.entrySet()) {
            String title = entry.getKey();
            List<IndexedSegment> indexedSegments = entry.getValue();

            int size = indexedSegments.size();
            for (int i = 0; i < size; i += maxSegmentsPerBatch) {
                List<IndexedSegment> batch = indexedSegments.subList(i, Math.min(i + maxSegmentsPerBatch, size));
                List<String> texts = batch.stream().map(is -> is.segment.text()).collect(Collectors.toList());

                EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
                if (taskType != null) {
                    configBuilder.taskType(taskType.getSdkTaskType());
                }
                if (outputDimensionality != null) {
                    configBuilder.outputDimensionality(outputDimensionality);
                }
                if (title != null) {
                    configBuilder.title(title);
                }

                EmbedContentResponse response = withRetryMappingExceptions(
                        () -> client.models.embedContent(modelName, texts, configBuilder.build()), maxRetries);

                if (response.embeddings().isPresent()) {
                    var embeddings = response.embeddings().get();
                    for (int j = 0; j < batch.size(); j++) {
                        if (j < embeddings.size() && embeddings.get(j).values().isPresent()) {
                            embeddingsArray[batch.get(j).index] =
                                    Embedding.from(embeddings.get(j).values().get());
                        }
                    }
                }
            }
        }

        return Response.from(Arrays.asList(embeddingsArray));
    }

    private static class IndexedSegment {
        final int index;
        final TextSegment segment;

        IndexedSegment(int index, TextSegment segment) {
            this.index = index;
            this.segment = segment;
        }
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
        private Integer maxSegmentsPerBatch = 100;
        private Integer maxRetries = 3;

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

        public Builder maxSegmentsPerBatch(Integer maxSegmentsPerBatch) {
            this.maxSegmentsPerBatch = maxSegmentsPerBatch;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleGenAiEmbeddingModel build() {
            return new GoogleGenAiEmbeddingModel(this);
        }
    }
}
