package dev.langchain4j.model.ollama;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

interface OllamaApi {

    @POST("/api/generate")
    @Headers({"Content-Type: application/json"})
    Call<CompletionResponse> completion(@Body CompletionRequest completionRequest);

    @POST("/api/generate")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingCompletion(@Body CompletionRequest completionRequest);

    @POST("/api/embeddings")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embedd(@Body EmbeddingRequest embeddingRequest);

    @POST("/api/chat")
    @Headers({"Content-Type: application/json"})
    Call<ChatResponse> chat(@Body ChatRequest chatRequest);

    @POST("/api/chat")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<ResponseBody> streamingChat(@Body ChatRequest chatRequest);
}
