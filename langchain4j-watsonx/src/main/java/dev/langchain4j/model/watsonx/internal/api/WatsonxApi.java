package dev.langchain4j.model.watsonx.internal.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface WatsonxApi {

    @POST("text/chat")
    Call<WatsonxAiChatCompletionResponse> chat(@Body WatsonxChatCompletionRequest request, @Query("version") String version);

}
