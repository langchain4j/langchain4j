package dev.langchain4j.model.bedrock;

import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public interface BedrockRuntimeClientFactory {
    BedrockRuntimeClient createClient(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            Duration timeout,
            boolean logRequests,
            boolean logResponses);
}
