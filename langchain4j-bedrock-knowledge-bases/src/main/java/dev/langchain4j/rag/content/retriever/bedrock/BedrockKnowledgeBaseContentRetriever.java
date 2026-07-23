package dev.langchain4j.rag.content.retriever.bedrock;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;

/**
 * A {@link ContentRetriever} that retrieves relevant content from an Amazon Bedrock Knowledge Base.
 * <br>
 * Amazon Bedrock Knowledge Bases allow you to give foundation models contextual information
 * from your private data sources for Retrieval Augmented Generation (RAG).
 * <br>
 * This retriever queries a specified Knowledge Base and returns matching content based on
 * semantic similarity to the query.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/knowledge-base.html">Amazon Bedrock Knowledge Bases</a>
 */
@Experimental
public class BedrockKnowledgeBaseContentRetriever implements ContentRetriever {

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final double DEFAULT_MIN_SCORE = 0.0;
    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);

    private final BedrockAgentRuntimeClient client;
    private final String knowledgeBaseId;
    private final int maxResults;
    private final double minScore;
    private final SearchType searchType;

    /**
     * Constructs a new {@link BedrockKnowledgeBaseContentRetriever} using the specified builder.
     *
     * @param builder The builder containing the configuration.
     */
    public BedrockKnowledgeBaseContentRetriever(Builder builder) {
        this.knowledgeBaseId = ensureNotBlank(builder.knowledgeBaseId, "knowledgeBaseId");
        this.maxResults = getOrDefault(builder.maxResults, DEFAULT_MAX_RESULTS);
        this.minScore = getOrDefault(builder.minScore, DEFAULT_MIN_SCORE);
        this.searchType = builder.searchType;

        if (builder.client != null) {
            this.client = builder.client;
        } else {
            Region region = getOrDefault(builder.region, DEFAULT_REGION);
            Duration timeout = getOrDefault(builder.timeout, DEFAULT_TIMEOUT);
            AwsCredentialsProvider credentialsProvider =
                    getOrDefault(builder.credentialsProvider, DefaultCredentialsProvider.create());
            boolean logRequests = getOrDefault(builder.logRequests, false);
            boolean logResponses = getOrDefault(builder.logResponses, false);

            this.client = createClient(region, timeout, credentialsProvider, logRequests, logResponses, builder.logger);
        }
    }

    private BedrockAgentRuntimeClient createClient(
            Region region,
            Duration timeout,
            AwsCredentialsProvider credentialsProvider,
            boolean logRequests,
            boolean logResponses,
            Logger logger) {
        return BedrockAgentRuntimeClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(config -> {
                    config.apiCallTimeout(timeout);
                    if (logRequests || logResponses) {
                        config.addExecutionInterceptor(
                                new BedrockKnowledgeBaseLoggingInterceptor(logRequests, logResponses, logger));
                    }
                })
                .build();
    }

    @Override
    public List<Content> retrieve(Query query) {
        KnowledgeBaseVectorSearchConfiguration.Builder vectorSearchConfigBuilder =
                KnowledgeBaseVectorSearchConfiguration.builder().numberOfResults(maxResults);

        if (searchType != null) {
            vectorSearchConfigBuilder.overrideSearchType(searchType);
        }

        KnowledgeBaseRetrievalConfiguration retrievalConfiguration = KnowledgeBaseRetrievalConfiguration.builder()
                .vectorSearchConfiguration(vectorSearchConfigBuilder.build())
                .build();

        RetrieveRequest retrieveRequest = RetrieveRequest.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .retrievalQuery(KnowledgeBaseQuery.builder().text(query.text()).build())
                .retrievalConfiguration(retrievalConfiguration)
                .build();

        RetrieveResponse response = client.retrieve(retrieveRequest);

        List<Content> contents = new ArrayList<>();

        for (KnowledgeBaseRetrievalResult result : response.retrievalResults()) {
            Double score = result.score();

            if (score != null && score < minScore) {
                continue;
            }

            TextSegment textSegment = toTextSegment(result);
            Map<ContentMetadata, Object> contentMetadata = new HashMap<>();

            if (score != null) {
                contentMetadata.put(ContentMetadata.SCORE, score);
            }

            contents.add(Content.from(textSegment, contentMetadata));
        }

        return contents;
    }

    private TextSegment toTextSegment(KnowledgeBaseRetrievalResult result) {
        RetrievalResultContent content = result.content();
        String text = content != null ? content.text() : "";

        Metadata metadata = extractMetadata(result);

        return TextSegment.from(text, metadata);
    }

    private Metadata extractMetadata(KnowledgeBaseRetrievalResult result) {
        Map<String, Object> metadataMap = new HashMap<>();

        RetrievalResultLocation location = result.location();
        if (location != null) {
            metadataMap.put("location_type", location.typeAsString());

            if (location.s3Location() != null) {
                metadataMap.put("s3_uri", location.s3Location().uri());
            }
            if (location.webLocation() != null) {
                metadataMap.put("web_url", location.webLocation().url());
            }
            if (location.confluenceLocation() != null) {
                metadataMap.put("confluence_url", location.confluenceLocation().url());
            }
            if (location.salesforceLocation() != null) {
                metadataMap.put("salesforce_url", location.salesforceLocation().url());
            }
            if (location.sharePointLocation() != null) {
                metadataMap.put("sharepoint_url", location.sharePointLocation().url());
            }
        }

        if (result.hasMetadata()) {
            for (Map.Entry<String, Document> entry : result.metadata().entrySet()) {
                String key = entry.getKey();
                Document value = entry.getValue();
                if (value != null) {
                    metadataMap.put(key, documentToValue(value));
                }
            }
        }

        return Metadata.from(metadataMap);
    }

    private Object documentToValue(Document document) {
        if (document.isString()) {
            return document.asString();
        } else if (document.isNumber()) {
            return document.asNumber();
        } else if (document.isBoolean()) {
            return document.asBoolean();
        } else if (document.isList()) {
            return document.asList().stream().map(this::documentToValue).toList();
        } else if (document.isMap()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, Document> entry : document.asMap().entrySet()) {
                map.put(entry.getKey(), documentToValue(entry.getValue()));
            }
            return map;
        }
        return document.toString();
    }

    /**
     * Creates a new builder for {@link BedrockKnowledgeBaseContentRetriever}.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BedrockKnowledgeBaseContentRetriever}.
     */
    public static class Builder {

        private BedrockAgentRuntimeClient client;
        private String knowledgeBaseId;
        private Integer maxResults;
        private Double minScore;
        private SearchType searchType;
        private Region region;
        private Duration timeout;
        private AwsCredentialsProvider credentialsProvider;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        /**
         * Sets a pre-configured {@link BedrockAgentRuntimeClient}.
         * If provided, region, timeout, and credentials settings will be ignored.
         *
         * @param client The BedrockAgentRuntimeClient to use.
         * @return builder
         */
        public Builder client(BedrockAgentRuntimeClient client) {
            this.client = client;
            return this;
        }

        /**
         * Sets the Knowledge Base ID to query. This is a required parameter.
         *
         * @param knowledgeBaseId The unique identifier of the Knowledge Base.
         * @return builder
         */
        public Builder knowledgeBaseId(String knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
            return this;
        }

        /**
         * Sets the maximum number of results to retrieve.
         * Default is 3.
         *
         * @param maxResults The maximum number of results.
         * @return builder
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum relevance score for the returned content.
         * Content scoring below this threshold will be excluded.
         * Default is 0.0.
         *
         * @param minScore The minimum score (between 0 and 1).
         * @return builder
         */
        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * Sets the search type to use.
         * <br>
         * - {@code SEMANTIC}: Uses vector embeddings only (default for most configurations).
         * <br>
         * - {@code HYBRID}: Uses both vector embeddings and raw text (available for OpenSearch Serverless with filterable text field).
         * <br>
         * If not specified, Amazon Bedrock will decide the search strategy automatically.
         *
         * @param searchType The search type.
         * @return builder
         */
        public Builder searchType(SearchType searchType) {
            this.searchType = searchType;
            return this;
        }

        /**
         * Sets the AWS region.
         * Default is US_EAST_1.
         *
         * @param region The AWS region.
         * @return builder
         */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /**
         * Sets the API call timeout.
         * Default is 1 minute.
         *
         * @param timeout The timeout duration.
         * @return builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the AWS credentials provider.
         * Default uses {@link DefaultCredentialsProvider}.
         *
         * @param credentialsProvider The credentials provider.
         * @return builder
         */
        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        /**
         * Enables logging of requests.
         *
         * @param logRequests Whether to log requests.
         * @return builder
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables logging of responses.
         *
         * @param logResponses Whether to log responses.
         * @return builder
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets a custom logger for request/response logging.
         *
         * @param logger The logger to use.
         * @return builder
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Builds a new {@link BedrockKnowledgeBaseContentRetriever} instance.
         *
         * @return A new retriever instance.
         */
        public BedrockKnowledgeBaseContentRetriever build() {
            return new BedrockKnowledgeBaseContentRetriever(this);
        }
    }
}
