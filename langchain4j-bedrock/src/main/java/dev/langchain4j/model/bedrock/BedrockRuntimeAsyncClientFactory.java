package dev.langchain4j.model.bedrock;

import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

public interface BedrockRuntimeAsyncClientFactory {
    BedrockRuntimeAsyncClient createAsyncClient(
            Region region,
            AwsCredentialsProvider credentialsProvider,
            Duration timeout,
            boolean logRequests,
            boolean logResponses);
}
