package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.genai.Batches;
import com.google.genai.Client;
import com.google.genai.Pager;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobDestination;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.InlinedRequest;
import com.google.genai.types.InlinedResponse;
import com.google.genai.types.JobState;
import com.google.genai.types.JobState.Known;
import com.google.genai.types.ListBatchJobsConfig;
import com.google.genai.types.Part;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleGenAiBatchChatModelTest {

    @Test
    void should_list_batch_jobs_with_pagination() throws Exception {
        Client client = mock(Client.class);
        Batches batchesService = mock(Batches.class);

        // Use reflection to set the public final 'batches' field on the mocked Client
        Field batchesField = Client.class.getDeclaredField("batches");
        batchesField.setAccessible(true);
        batchesField.set(client, batchesService);

        Pager pager = mock(Pager.class);
        when(batchesService.list(any(ListBatchJobsConfig.class))).thenReturn(pager);

        BatchJob batchJob1 = mock(BatchJob.class);
        when(batchJob1.name()).thenReturn(Optional.of("batches/1"));

        JobState jobState = mock(JobState.class);
        when(jobState.knownEnum()).thenReturn(Known.JOB_STATE_RUNNING);
        when(batchJob1.state()).thenReturn(Optional.of(jobState));

        when(pager.page()).thenReturn(ImmutableList.of(batchJob1));

        // Use reflection to set the protected nextPageToken on the pager superclass (BasePager)
        Field field = pager.getClass().getSuperclass().getDeclaredField("nextPageToken");
        field.setAccessible(true);
        field.set(pager, "token-123");

        GoogleGenAiBatchChatModel batchModel = GoogleGenAiBatchChatModel.builder()
                .client(client)
                .modelName("gemini-2.5-flash")
                .build();

        BatchPage<ChatResponse> response = batchModel.list(new BatchPagination(10, null));

        assertThat(response).isNotNull();
        assertThat(response.batches()).hasSize(1);
        assertThat(response.batches().get(0).batchId()).isEqualTo("batches/1");
        assertThat(response.nextPageToken()).isEqualTo("token-123");
    }

    @Test
    void should_invoke_generate_content_config_customizer_when_building_the_request() {
        RuntimeException fromCustomizer = new RuntimeException("customizer invoked");
        GoogleGenAiBatchChatModel batchModel = GoogleGenAiBatchChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-2.5-flash")
                .generateContentConfigCustomizer(config -> {
                    throw fromCustomizer;
                })
                .build();

        // customizer runs while assembling the inlined request, before any network access
        assertThatThrownBy(() -> batchModel.submit(
                        "test-batch",
                        List.of(ChatRequest.builder()
                                .messages(UserMessage.from("hello"))
                                .build())))
                .isSameAs(fromCustomizer);
    }

    @Test
    void should_wire_thinking_options_into_the_batch_request() throws Exception {
        Client client = mock(Client.class);
        Batches batchesService = mock(Batches.class);
        Field batchesField = Client.class.getDeclaredField("batches");
        batchesField.setAccessible(true);
        batchesField.set(client, batchesService);

        BatchJob batchJob = mock(BatchJob.class);
        when(batchJob.name()).thenReturn(Optional.of("batches/1"));
        JobState jobState = mock(JobState.class);
        when(jobState.knownEnum()).thenReturn(Known.JOB_STATE_RUNNING);
        when(batchJob.state()).thenReturn(Optional.of(jobState));
        when(batchesService.create(any(String.class), any(BatchJobSource.class), any(CreateBatchJobConfig.class)))
                .thenReturn(batchJob);

        GoogleGenAiBatchChatModel batchModel = GoogleGenAiBatchChatModel.builder()
                .client(client)
                .modelName("gemini-2.5-flash")
                .includeThoughts(true)
                .sendThinking(true)
                .build();

        AiMessage previousAnswer =
                AiMessage.builder().text("42").thinking("Six times seven.").build();
        ChatRequest request = ChatRequest.builder()
                .modelName("gemini-2.5-flash")
                .messages(UserMessage.from("What is 6 times 7?"), previousAnswer, UserMessage.from("Are you sure?"))
                .build();

        batchModel.submit(new BatchRequest<>(List.of(request)));

        ArgumentCaptor<BatchJobSource> sourceCaptor = ArgumentCaptor.forClass(BatchJobSource.class);
        verify(batchesService).create(any(String.class), sourceCaptor.capture(), any(CreateBatchJobConfig.class));

        InlinedRequest inlined =
                sourceCaptor.getValue().inlinedRequests().orElseThrow().get(0);

        assertThat(inlined.config().orElseThrow().thinkingConfig().orElseThrow().includeThoughts())
                .hasValue(true);

        List<Part> aiParts = inlined.contents().orElseThrow().get(1).parts().orElseThrow();
        assertThat(aiParts.get(0).thought()).hasValue(true);
        assertThat(aiParts.get(0).text()).hasValue("Six times seven.");
    }

    @Test
    void should_map_thought_summary_from_a_completed_batch_when_return_thinking_is_enabled() throws Exception {
        Client client = mock(Client.class);
        Batches batchesService = mock(Batches.class);
        Field batchesField = Client.class.getDeclaredField("batches");
        batchesField.setAccessible(true);
        batchesField.set(client, batchesService);

        Content content = Content.builder()
                .parts(
                        Part.builder().text("Six times seven.").thought(true).build(),
                        Part.builder().text("42").build())
                .build();
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(Candidate.builder().content(content).build())
                .build();
        BatchJob batchJob = BatchJob.builder()
                .name("batches/1")
                .model("gemini-2.5-flash")
                .state(new JobState(Known.JOB_STATE_SUCCEEDED.toString()))
                .dest(BatchJobDestination.builder()
                        .inlinedResponses(
                                InlinedResponse.builder().response(response).build())
                        .build())
                .build();
        when(batchesService.get(any(String.class), any())).thenReturn(batchJob);

        GoogleGenAiBatchChatModel batchModel = GoogleGenAiBatchChatModel.builder()
                .client(client)
                .modelName("gemini-2.5-flash")
                .returnThinking(true)
                .build();

        AiMessage aiMessage =
                batchModel.retrieve("batches/1").results().get(0).response().aiMessage();

        assertThat(aiMessage.thinking()).isEqualTo("Six times seven.");
        assertThat(aiMessage.text()).isEqualTo("42");
    }
}
