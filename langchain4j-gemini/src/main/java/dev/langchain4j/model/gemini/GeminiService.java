package dev.langchain4j.model.gemini;

//import io.reactivex.rxjava3.core.Observable;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
//import retrofit2.http.Streaming;

interface GeminiService {

    @POST("models/{model}:generateContent")
    Call<GeminiGenerateContentResponse> generateContent(
        @Path("model") String modelName,
        @Query("key") String apiKey,
        @Body GeminiGenerateContentRequest request);

/*
    @Streaming
    @POST("models/{model}:streamGenerateContent")
    Observable<GeminiGenerateContentResponse> streamGenerateContent(
        @Path("model") String modelName,
        @Query("key") String apiKey,
        @Body GeminiGenerateContentRequest request);
*/


}
