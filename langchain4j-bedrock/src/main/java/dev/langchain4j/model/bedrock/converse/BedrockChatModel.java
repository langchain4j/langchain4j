package dev.langchain4j.model.bedrock.converse;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class BedrockChatModel implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(BedrockChatModel.class);

    public static final String DEFAULT_MODEL_ID = "anthropic.claude-3-5-sonnet-20241022-v2:0";

    private Region region;
    private AwsCredentialsProvider credentialsProvider;
    private String modelId;
    private InferenceConfiguration inferenceConfiguration;
    private Integer maxRetries;
    private Duration timeout;
    private BedrockRuntimeClient client;

    public BedrockChatModel() {
        this.region = Region.US_EAST_1;
        this.credentialsProvider = DefaultCredentialsProvider.builder().build();
        this.modelId = DEFAULT_MODEL_ID;
        this.inferenceConfiguration = InferenceConfiguration.builder().build();
        this.maxRetries = 5;
        this.timeout = Duration.ofMinutes(1L);
        this.client = createClient();
    }

    public BedrockChatModel(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            String modelId,
            InferenceConfiguration inferenceConfiguration,
            Integer maxRetries,
            Duration timeout
    ) {
        this.region = region;
        this.credentialsProvider = credentialsProvider;
        this.modelId = modelId;
        this.inferenceConfiguration = inferenceConfiguration;
        this.maxRetries = maxRetries;
        this.timeout = timeout;
        this.client = createClient();
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages) {
        return generate(messages, emptyList());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, toolSpecification, singletonList(toolSpecification));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, null, toolSpecifications);
    }

    private Response<AiMessage> generate(
            List<ChatMessage> messages,
            ToolSpecification toolChoiceSpecification,
            List<ToolSpecification> toolSpecifications
    ) {
        return null;
    }

    public static BedrockChatModelBuilder builder() {
        return new BedrockChatModelBuilder();
    }

    private BedrockRuntimeClient createClient() {
        return BedrockRuntimeClient.builder()
                .region(this.region)
                .credentialsProvider(this.credentialsProvider)
                .overrideConfiguration(config -> config.apiCallTimeout(this.timeout))
                .build();
    }

    public static class BedrockChatModelBuilder {

        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private String modelId;
        private InferenceConfiguration inferenceConfiguration;
        private Integer maxRetries;
        private Duration timeout;

        public BedrockChatModelBuilder region(Region region) {
            this.region = region;
            return this;
        }

        public BedrockChatModelBuilder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        public BedrockChatModelBuilder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public BedrockChatModelBuilder inferenceConfiguration(InferenceConfiguration inferenceConfiguration) {
            this.inferenceConfiguration = inferenceConfiguration;
            return this;
        }

        public BedrockChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public BedrockChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public BedrockChatModel build() {
            return new BedrockChatModel(region, credentialsProvider, modelId, inferenceConfiguration, maxRetries, timeout);
        }

    }
}
