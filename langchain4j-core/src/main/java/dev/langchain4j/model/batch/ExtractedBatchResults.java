package dev.langchain4j.model.batch;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

public record ExtractedBatchResults<T>(List<T> responses, List<Status> errors) {

    public record Status(int code, String message, @Nullable List<Map<String, Object>> details) {
    }
}
