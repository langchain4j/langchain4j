package dev.langchain4j.model.google.genai;

import com.google.genai.Client;
import com.google.genai.Pager;
import com.google.genai.types.BatchJob;
import com.google.genai.types.JobError;
import com.google.genai.types.JobState;
import com.google.genai.types.ListBatchJobsConfig;
import dev.langchain4j.model.batch.BatchError;
import dev.langchain4j.model.batch.BatchPage;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.batch.BatchState;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class GoogleGenAiBatchUtils {

    private GoogleGenAiBatchUtils() {}

    static <T> BatchPage<T> listBatchJobs(
            Client client, Integer pageSize, String pageToken, Function<BatchJob, BatchResponse<T>> mapper) {
        ListBatchJobsConfig.Builder builder = ListBatchJobsConfig.builder();
        if (pageSize != null) {
            builder.pageSize(pageSize);
        }
        if (pageToken != null) {
            builder.pageToken(pageToken);
        }

        Pager pager = client.batches.list(builder.build());

        List<BatchResponse<T>> batches = new ArrayList<>();
        if (pager.page() != null) {
            for (Object obj : pager.page()) {
                if (obj instanceof BatchJob batchJob) {
                    batches.add(mapper.apply(batchJob));
                }
            }
        }

        String nextPageToken = null;
        try {
            // The nextPageToken field on BasePager should be accessible but is currently protected
            // without a public getter in the SDK, so we retrieve it using reflection.
            Field field = pager.getClass().getSuperclass().getDeclaredField("nextPageToken");
            field.setAccessible(true);
            nextPageToken = (String) field.get(pager);
        } catch (Exception e) {
            // ignore/fallback
        }

        return new BatchPage<>(batches, nextPageToken);
    }

    static BatchState toBatchState(JobState.Known state) {
        if (state == null) {
            return BatchState.UNSPECIFIED;
        }
        switch (state) {
            case JOB_STATE_PENDING:
                return BatchState.PENDING;
            case JOB_STATE_RUNNING:
            case JOB_STATE_CANCELLING:
                return BatchState.RUNNING;
            case JOB_STATE_SUCCEEDED:
                return BatchState.SUCCEEDED;
            case JOB_STATE_FAILED:
                return BatchState.FAILED;
            case JOB_STATE_CANCELLED:
                return BatchState.CANCELLED;
            case JOB_STATE_EXPIRED:
                return BatchState.EXPIRED;
            default:
                return BatchState.UNSPECIFIED;
        }
    }

    static BatchError toBatchError(JobError error) {
        Integer code = 0;
        String message = "Batch job failed";
        if (error != null) {
            code = error.code().orElse(0);
            message = error.message().orElse("Batch job failed");
        }
        return new BatchError(code, message, new ArrayList<>());
    }
}
