package dev.langchain4j.store.embedding.vearch;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface VearchApi {

    @POST("/document/upsert")
    @Headers({"Content-Type: application/json"})
    Call<JsonObject> documentUpsert(@Body DocumentUpsertRequest documentUpsertRequest);

    @POST("/document/search")
    @Headers({"Content-Type: application/json"})
    Call<JsonObject> documentSearch(@Body DocumentSearchRequest documentSearchRequest);

}
