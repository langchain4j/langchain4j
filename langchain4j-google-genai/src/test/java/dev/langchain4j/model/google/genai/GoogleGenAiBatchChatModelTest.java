package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.genai.Batches;
import com.google.genai.Client;
import com.google.genai.Pager;
import com.google.genai.types.BatchJob;
import com.google.genai.types.JobState;
import com.google.genai.types.JobState.Known;
import com.google.genai.types.ListBatchJobsConfig;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchPagination;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
}
