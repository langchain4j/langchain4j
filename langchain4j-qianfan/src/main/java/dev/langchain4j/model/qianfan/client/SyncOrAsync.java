package dev.langchain4j.model.qianfan.client;

import java.util.function.Consumer;

public  interface SyncOrAsync<ResponseContent> {
    ResponseContent execute();

    AsyncResponseHandling onResponse(Consumer<ResponseContent> var1);
}
