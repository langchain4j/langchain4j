package dev.langchain4j.model.wenxin.client;

public interface StreamingResponseHandling extends AsyncResponseHandling {
    StreamingCompletionHandling onComplete(Runnable var1);
}
