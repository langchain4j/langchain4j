package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchIncomplete;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicBatchChatModelIT {

    private static final String MODEL_NAME = "claude-3-5-haiku-20241022";

    private AnthropicBatchChatModel subject;
    private AnthropicBatchName createdBatchName;

    @BeforeEach
    void setUp() {
        subject = AnthropicBatchChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(MODEL_NAME)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        // Cancel any created batch to clean up
        if (createdBatchName != null) {
            try {
                subject.cancelBatchJob(createdBatchName);
            } catch (Exception e) {
                // Ignore errors during cleanup (batch may already be completed/cancelled)
            }
            createdBatchName = null;
        }
    }

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_single_request() {
            // given
            var requests = List.of(ChatRequest.builder()
                    .messages(UserMessage.from("What is the capital of France?"))
                    .build());

            // when
            var result = subject.createBatchInline(requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<ChatResponse>) result;
            assertThat(incomplete.name().id()).startsWith("msgbatch_");
            assertThat(incomplete.processingStatus()).isEqualTo("in_progress");
            assertThat(incomplete.processingCount()).isGreaterThanOrEqualTo(0);

            createdBatchName = incomplete.name();
        }

        @Test
        void should_create_batch_with_multiple_requests() {
            // given
            var requests = List.of(
                    ChatRequest.builder()
                            .messages(UserMessage.from("What is the capital of France?"))
                            .build(),
                    ChatRequest.builder()
                            .messages(UserMessage.from("What is the capital of Germany?"))
                            .build(),
                    ChatRequest.builder()
                            .messages(UserMessage.from("What is the capital of Italy?"))
                            .build());

            // when
            var result = subject.createBatchInline(requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<ChatResponse>) result;
            assertThat(incomplete.name().id()).startsWith("msgbatch_");
            assertThat(incomplete.processingStatus()).isEqualTo("in_progress");

            createdBatchName = incomplete.name();
        }

        @Test
        void should_create_batch_with_custom_id() {
            // given
            var customId = "my-custom-request-id";
            var requests = List.of(
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build());

            // when
            var result = subject.createBatchInline(customId, requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<ChatResponse>) result;
            assertThat(incomplete.name().id()).startsWith("msgbatch_");

            createdBatchName = incomplete.name();
        }
    }

    @Nested
    class RetrieveBatchResults {

        @Test
        void should_retrieve_batch_status() {
            // given - create a batch first
            var requests = List.of(ChatRequest.builder()
                    .messages(UserMessage.from("What is 2+2?"))
                    .build());
            var createResult = subject.createBatchInline(requests);
            var batchName = createResult.name();
            createdBatchName = batchName;

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<ChatResponse>) result;
            assertThat(incomplete.name()).isEqualTo(batchName);
            assertThat(incomplete.processingStatus()).isIn("in_progress", "canceling", "ended");
        }

        @Test
        void should_fail_for_non_existent_batch() {
            // given
            var nonExistentBatchName = AnthropicBatchName.of("msgbatch_nonexistent123456789");

            // when & then
            assertThatThrownBy(() -> subject.retrieveBatchResults(nonExistentBatchName))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class CancelBatchJob {

        @Test
        void should_cancel_in_progress_batch() {
            // given - create a batch first
            var requests = List.of(ChatRequest.builder()
                    .messages(UserMessage.from("Write a long essay about quantum physics"))
                    .build());
            var createResult = subject.createBatchInline(requests);
            var batchName = ((AnthropicBatchIncomplete<ChatResponse>) createResult).name();

            // when
            subject.cancelBatchJob(batchName);

            // then - verify the batch is now canceling or ended
            var result = subject.retrieveBatchResults(batchName);
            if (result instanceof AnthropicBatchIncomplete<ChatResponse> incomplete) {
                assertThat(incomplete.processingStatus()).isIn("canceling", "ended");
            }
            // If it's already ended (BatchSuccess or BatchError), that's also acceptable
        }

        @Test
        void should_fail_for_non_existent_batch() {
            // given
            var nonExistentBatchName = AnthropicBatchName.of("msgbatch_nonexistent123456789");

            // when & then
            assertThatThrownBy(() -> subject.cancelBatchJob(nonExistentBatchName))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class DeleteBatchJob {

        @Test
        void should_throw_unsupported_operation_exception() {
            // given
            var batchName = AnthropicBatchName.of("msgbatch_anyid123");

            // when & then
            assertThatThrownBy(() -> subject.deleteBatchJob(batchName))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Anthropic API does not support deleting batches");
        }
    }

    @Nested
    class ListBatchJobs {

        @Test
        void should_list_batch_jobs() {
            // given - create a batch to ensure at least one exists
            var requests = List.of(
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build());
            var createResult = subject.createBatchInline(requests);
            createdBatchName = createResult.name();

            // when
            var result = subject.listBatchJobs(null, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.batches()).isNotEmpty();
        }

        @Test
        void should_list_batch_jobs_with_page_size() {
            // given
            int pageSize = 5;

            // when
            var result = subject.listBatchJobs(pageSize, null);

            // then
            assertThat(result).isNotNull();
            assertThat(result.batches()).hasSizeLessThanOrEqualTo(pageSize);
        }

        @Test
        void should_support_pagination() {
            // given - create multiple batches to ensure pagination is possible
            for (int i = 0; i < 3; i++) {
                var requests = List.of(ChatRequest.builder()
                        .messages(UserMessage.from("Request " + i))
                        .build());
                var createResult = subject.createBatchInline(requests);
                if (i == 2) {
                    createdBatchName = createResult.name();
                }
            }

            // when - get first page
            var firstPage = subject.listBatchJobs(2, null);

            // then
            assertThat(firstPage).isNotNull();
            if (firstPage.hasMore() && firstPage.nextPageToken() != null) {
                // when - get next page
                var secondPage = subject.listBatchJobs(2, firstPage.nextPageToken());

                // then
                assertThat(secondPage).isNotNull();
            }
        }
    }

    @Nested
    class BuilderConfiguration {

        @Test
        void should_fail_without_api_key() {
            var builder = AnthropicBatchChatModel.builder().modelName(MODEL_NAME);

            assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_create_model_with_all_parameters() {
            // given & when
            var model = AnthropicBatchChatModel.builder()
                    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                    .baseUrl("https://api.anthropic.com/v1/")
                    .version("2023-06-01")
                    .modelName(MODEL_NAME)
                    .temperature(0.7)
                    .topP(0.9)
                    .topK(40)
                    .maxTokens(100)
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            var requests = List.of(
                    ChatRequest.builder().messages(UserMessage.from("Hi")).build());

            // when
            var result = model.createBatchInline(requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            createdBatchName = result.name();
        }
    }

    @Nested
    class BatchNameValidation {

        @Test
        void should_accept_valid_batch_name() {
            // given & when
            var batchName = AnthropicBatchName.of("msgbatch_01HkcTjaV5uDC8jWR4ZsDV8d");

            // then
            assertThat(batchName.id()).isEqualTo("msgbatch_01HkcTjaV5uDC8jWR4ZsDV8d");
            assertThat(batchName).hasToString("msgbatch_01HkcTjaV5uDC8jWR4ZsDV8d");
        }

        @Test
        void should_reject_invalid_batch_name_prefix() {
            assertThatThrownBy(() -> AnthropicBatchName.of("batch_123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("msgbatch_");
        }

        @Test
        void should_reject_null_batch_name() {
            assertThatThrownBy(() -> AnthropicBatchName.of(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_reject_blank_batch_name() {
            assertThatThrownBy(() -> AnthropicBatchName.of("   ")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
