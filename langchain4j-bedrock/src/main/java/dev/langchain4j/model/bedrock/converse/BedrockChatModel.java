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
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;

import java.util.List;

public class BedrockChatModel implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(BedrockChatModel.class);

    public static final String DEFAULT_MODEL_ID = "default-bedrock-model-id";

    private Region region;
    private AwsCredentialsProvider credentialsProvider;
    private String modelId;
    private InferenceConfiguration inferenceConfiguration;

    public BedrockChatModel() {
        this.region = Region.US_EAST_1;
        this.credentialsProvider = DefaultCredentialsProvider.builder().build();
        this.modelId = DEFAULT_MODEL_ID;
        this.inferenceConfiguration = InferenceConfiguration.builder().build();
    }

    public BedrockChatModel(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            String modelId,
            InferenceConfiguration inferenceConfiguration
    ) {
        this.region = region;
        this.credentialsProvider = credentialsProvider;
        this.modelId = modelId;
        this.inferenceConfiguration = inferenceConfiguration;
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages) {
        return generate(messages, (List<ToolSpecification>) null);
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages, final List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Not implemented yet");
    }

    public static BedrockChatModelBuilder builder() {
        return new BedrockChatModelBuilder();
    }

    public static class BedrockChatModelBuilder {

        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private String modelId;
        private InferenceConfiguration inferenceConfiguration;

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

        public BedrockChatModel build() {
            return new BedrockChatModel(region, credentialsProvider, modelId, inferenceConfiguration);
        }

    }
}
