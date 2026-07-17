package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Live integration test for {@link BedrockBatchChatModel}. Requires an AWS account with Bedrock model access, an S3
 * bucket, and a service role, so it is skipped in CI (gated on {@code BEDROCK_BATCH_ROLE_ARN}). Batch jobs run
 * asynchronously and can take several minutes, so this is a slow, manual test. It is self-contained: it writes under a
 * unique S3 prefix and deletes everything it created afterwards.
 *
 * <p>Environment: {@code BEDROCK_BATCH_ROLE_ARN}, {@code BEDROCK_BATCH_BUCKET}, optional {@code BEDROCK_BATCH_MODEL}
 * (default {@code anthropic.claude-3-haiku-20240307-v1:0}) and {@code AWS_REGION} (default {@code us-east-1}).
 * The model must be one that supports batch inference. Credentials come from the default AWS chain.</p>
 */
@EnabledIfEnvironmentVariable(named = "BEDROCK_BATCH_ROLE_ARN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BEDROCK_BATCH_BUCKET", matches = ".+")
class BedrockBatchChatModelIT {

    private static final int RECORD_COUNT = 100;

    private final Region region = Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1"));
    private final String bucket = System.getenv("BEDROCK_BATCH_BUCKET");
    private final String roleArn = System.getenv("BEDROCK_BATCH_ROLE_ARN");
    private final String model =
            System.getenv().getOrDefault("BEDROCK_BATCH_MODEL", "anthropic.claude-3-haiku-20240307-v1:0");
    private final String prefix = "langchain4j-batch-it/" + UUID.randomUUID();

    private final S3Client s3 = S3Client.builder().region(region).build();

    private BedrockBatchChatModel model() {
        return BedrockBatchChatModel.builder()
                .region(region)
                .modelId(model)
                .roleArn(roleArn)
                .outputS3Uri("s3://" + bucket + "/" + prefix)
                .defaultRequestParameters(DefaultChatRequestParameters.builder()
                        .maxOutputTokens(16)
                        .build())
                .build();
    }

    @Test
    void submits_polls_and_reads_results() throws Exception {
        List<ChatRequest> requests = new ArrayList<>();
        for (int i = 0; i < RECORD_COUNT; i++) {
            requests.add(ChatRequest.builder()
                    .messages(UserMessage.from("Reply with the single word: ok"))
                    .build());
        }

        BedrockBatchChatModel model = model();
        BatchResponse<ChatResponse> submitted = model.submit(new dev.langchain4j.model.batch.BatchRequest<>(requests));
        assertThat(submitted.batchId()).isNotBlank();
        assertThat(submitted.state()).isEqualTo(BatchState.PENDING);

        BatchResponse<ChatResponse> result = pollUntilTerminal(model, submitted.batchId());
        assertThat(result.state()).isEqualTo(BatchState.SUCCEEDED);
        assertThat(result.results()).hasSize(RECORD_COUNT);
        assertThat(result.results().get(0).isSuccess()).isTrue();
        assertThat(result.results().get(0).response().aiMessage().text()).isNotBlank();
    }

    @Test
    void lists_jobs() {
        assertThat(model().list(null).batches()).isNotNull();
    }

    private BatchResponse<ChatResponse> pollUntilTerminal(BedrockBatchChatModel model, String batchId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + MINUTES.toMillis(60);
        while (System.currentTimeMillis() < deadline) {
            BatchResponse<ChatResponse> response = model.retrieve(batchId);
            BatchState state = response.state();
            if (state == BatchState.SUCCEEDED
                    || state == BatchState.FAILED
                    || state == BatchState.CANCELLED
                    || state == BatchState.EXPIRED) {
                return response;
            }
            MINUTES.sleep(1);
        }
        throw new AssertionError("Batch job did not complete within the timeout");
    }

    @AfterEach
    void cleanup() {
        List<ObjectIdentifier> objects = s3.listObjectsV2(b -> b.bucket(bucket).prefix(prefix)).contents().stream()
                .map(S3Object::key)
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        if (!objects.isEmpty()) {
            s3.deleteObjects(b -> b.bucket(bucket).delete(d -> d.objects(objects)));
        }
    }
}
