package dev.langchain4j.model.qianfan.client;

public interface StreamingResponseHandling extends AsyncResponseHandling {
    StreamingCompletionHandling onComplete(Runnable var1);
}
