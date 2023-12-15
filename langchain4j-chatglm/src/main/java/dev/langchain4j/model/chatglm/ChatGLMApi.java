package dev.langchain4j.model.chatglm;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ChatGLMApi {

    @POST()
    @Headers({"Content-Type: application/json"})
    Call<CompletionResponse> completion(@Body CompletionRequest completionRequest);
}
