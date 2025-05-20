package dev.langchain4j.model.mistralai.internal.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

public interface MistralAiApi {

    @POST("chat/completions")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistral-ai"})
    Call<MistralAiChatCompletionResponse> chatCompletion(@Body MistralAiChatCompletionRequest request);

    @POST("chat/completions")
    @Headers({"Content-Type: application/json", "Accept: text/event-stream", "User-Agent: langchain4j-mistral-ai"})
    @Streaming
    Call<ResponseBody> streamingChatCompletion(@Body MistralAiChatCompletionRequest request);

    @POST("embeddings")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistral-ai"})
    Call<MistralAiEmbeddingResponse> embedding(@Body MistralAiEmbeddingRequest request);

    @POST("moderations")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistral-ai"})
    Call<MistralAiModerationResponse> moderations(@Body MistralAiModerationRequest request);

    @GET("models")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistral-ai"})
    Call<MistralAiModelResponse> models();

    @POST("fim/completions")
    @Headers({"Content-Type: application/json", "Accept: application/json", "User-Agent: langchain4j-mistral-ai"})
    Call<MistralAiChatCompletionResponse> fimCompletion(@Body MistralAiFimCompletionRequest request);

    @POST("fim/completions")
    @Headers({"Content-Type: application/json", "Accept: text/event-stream", "User-Agent: langchain4j-mistral-ai"})
    @Streaming
    Call<ResponseBody> streamingFimCompletion(@Body MistralAiFimCompletionRequest request);
}
