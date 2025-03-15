package dev.langchain4j.model.novitaai.client;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

/**
 * Public interface to interact with the WorkerAI API.
 */
public interface NovitaAiApi {

    /**
     * Generate chat.
     *
     * @param apiRequest
     *      request.
     * @return
     *      response.
     */
    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<NovitaAiChatCompletionResponse> generateChat(@Body NovitaAiChatCompletionRequest apiRequest);

    /**
     * Generate chat.
     *
     * @param apiRequest
     *      request.
     * @return
     *      response.
     */
    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<NovitaAiChatCompletionResponse> streamingChatCompletion(@Body NovitaAiChatCompletionRequest apiRequest);

}
