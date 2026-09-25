package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchItemResult;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

@EnabledIfEnvironmentVariable(named = "BEDROCK_BATCH_ROLE_ARN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BEDROCK_BATCH_BUCKET", matches = ".+")
class BedrockBatchChatModelIT {

    private static final int RECORD_COUNT = 100;

    private static final Region REGION = Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1"));
    private static final String BUCKET = System.getenv("BEDROCK_BATCH_BUCKET");
    private static final String PREFIX = "langchain4j-batch-it/" + UUID.randomUUID();

    private static BedrockClient bedrock;
    private static S3Client s3;
    private static BedrockBatchChatModel model;

    private String unfinishedBatchId;

    @BeforeAll
    static void setUp() {
        bedrock = BedrockClient.builder().region(REGION).build();
        s3 = S3Client.builder().region(REGION).build();
        model = BedrockBatchChatModel.builder()
                .bedrockClient(bedrock)
                .s3Client(s3)
                .modelId(System.getenv().getOrDefault("BEDROCK_BATCH_MODEL", "anthropic.claude-3-haiku-20240307-v1:0"))
                .roleArn(System.getenv("BEDROCK_BATCH_ROLE_ARN"))
                .outputS3Uri("s3://" + BUCKET + "/" + PREFIX)
                .jobTimeout(Duration.ofHours(24))
                .defaultRequestParameters(DefaultChatRequestParameters.builder()
                        .temperature(0.0)
                        .maxOutputTokens(16)
                        .build())
                .build();
    }

    @Test
    void should_return_each_result_at_the_position_of_its_request() throws Exception {
        List<ChatRequest> requests = new ArrayList<>();
        for (int i = 0; i < RECORD_COUNT; i++) {
            requests.add(ChatRequest.builder()
                    .messages(UserMessage.from("Reply with only the number " + i + " and nothing else."))
                    .build());
        }

        BatchResponse<ChatResponse> submitted = model.submit(new BatchRequest<>(requests));
        unfinishedBatchId = submitted.batchId();
        assertThat(submitted.state()).isEqualTo(BatchState.PENDING);
        assertThat(isListed(submitted.batchId())).isTrue();

        BatchResponse<ChatResponse> result = pollUntilTerminal(submitted.batchId());
        unfinishedBatchId = null;
        assertThat(result.state())
                .as("job %s ended with these errors: %s", result.batchId(), errorMessages(result))
                .isEqualTo(BatchState.SUCCEEDED);
        assertThat(result.results()).hasSize(RECORD_COUNT);
        for (int i = 0; i < RECORD_COUNT; i++) {
            BatchItemResult<ChatResponse> item = result.results().get(i);
            assertThat(item.isSuccess())
                    .as("request %d failed: %s", i, item.error())
                    .isTrue();
            assertThat(item.response().aiMessage().text().replaceAll("\\D", ""))
                    .as("result of request %d", i)
                    .isEqualTo(String.valueOf(i));
        }
    }

    private static boolean isListed(String batchId) {
        BatchPagination pagination = null;
        do {
            BatchPage<ChatResponse> page = model.list(pagination);
            if (page.batches().stream().anyMatch(batch -> batch.batchId().equals(batchId))) {
                return true;
            }
            pagination = page.nextPageToken() != null ? new BatchPagination(100, page.nextPageToken()) : null;
        } while (pagination != null);
        return false;
    }

    private static BatchResponse<ChatResponse> pollUntilTerminal(String batchId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + MINUTES.toMillis(60);
        while (System.currentTimeMillis() < deadline) {
            BatchResponse<ChatResponse> response = model.retrieve(batchId);
            if (response.state().isTerminal()) {
                return response;
            }
            MINUTES.sleep(1);
        }
        throw new AssertionError("Batch job " + batchId + " did not complete within the timeout");
    }

    private static String errorMessages(BatchResponse<ChatResponse> response) {
        return response.results().stream()
                .filter(item -> !item.isSuccess())
                .map(item -> item.error().message())
                .distinct()
                .collect(joining("; "));
    }

    @AfterEach
    void cancelUnfinishedJob() {
        if (unfinishedBatchId != null) {
            model.cancel(unfinishedBatchId);
        }
    }

    @AfterAll
    static void cleanUp() {
        List<ObjectIdentifier> objects =
                s3.listObjectsV2(builder -> builder.bucket(BUCKET).prefix(PREFIX)).contents().stream()
                        .map(S3Object::key)
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .toList();
        if (!objects.isEmpty()) {
            s3.deleteObjects(builder -> builder.bucket(BUCKET).delete(delete -> delete.objects(objects)));
        }
        model.close();
        bedrock.close();
        s3.close();
    }
}
