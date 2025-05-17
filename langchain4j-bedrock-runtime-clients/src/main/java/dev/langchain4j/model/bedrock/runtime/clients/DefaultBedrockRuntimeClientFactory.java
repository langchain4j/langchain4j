package dev.langchain4j.model.bedrock.runtime.clients;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.model.bedrock.BedrockRuntimeClientFactory;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

public class DefaultBedrockRuntimeClientFactory implements BedrockRuntimeClientFactory {
    @Override
    public BedrockRuntimeClient createClient(
            final Region region,
            final AwsCredentialsProvider credentialsProvider,
            final Duration timeout,
            final boolean logRequests,
            final boolean logResponses) {
        final BedrockRuntimeClientBuilder bedrockRuntimeClientBuilder = BedrockRuntimeClient.builder()
                .region(region)
                .credentialsProvider(
                        isNull(credentialsProvider) ? DefaultCredentialsProvider.create() : credentialsProvider);

        if (nonNull(timeout)) {
            bedrockRuntimeClientBuilder.httpClientBuilder(
                    ApacheHttpClient.builder().socketTimeout(timeout));
        }

        return bedrockRuntimeClientBuilder
                .overrideConfiguration(config -> {
                    if (nonNull(timeout)) config.apiCallTimeout(timeout);
                    if (logRequests || logResponses)
                        config.addExecutionInterceptor(new AwsLoggingInterceptor(logRequests, logResponses));
                })
                .build();
    }
}
