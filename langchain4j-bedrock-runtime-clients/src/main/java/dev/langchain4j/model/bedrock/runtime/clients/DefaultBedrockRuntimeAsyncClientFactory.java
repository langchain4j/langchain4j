package dev.langchain4j.model.bedrock.runtime.clients;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.model.bedrock.BedrockRuntimeAsyncClientFactory;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;

public class DefaultBedrockRuntimeAsyncClientFactory implements BedrockRuntimeAsyncClientFactory {
    @Override
    public BedrockRuntimeAsyncClient createAsyncClient(
            final Region region,
            final AwsCredentialsProvider credentialsProvider,
            final Duration timeout,
            final boolean logRequests,
            final boolean logResponses) {
        final BedrockRuntimeAsyncClientBuilder bedrockRuntimeAsyncClientBuilder = BedrockRuntimeAsyncClient.builder()
                .region(region)
                .credentialsProvider(
                        isNull(credentialsProvider) ? DefaultCredentialsProvider.create() : credentialsProvider);
        if (nonNull(timeout)) {
            bedrockRuntimeAsyncClientBuilder.httpClientBuilder(
                    NettyNioAsyncHttpClient.builder().readTimeout(timeout));
        }
        return bedrockRuntimeAsyncClientBuilder
                .overrideConfiguration(config -> {
                    if (nonNull(timeout)) config.apiCallTimeout(timeout);
                    if (logRequests || logResponses)
                        config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses));
                })
                .build();
    }
}
