package dev.langchain4j.web.search.searchapi;

import java.util.Map;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Headers;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.GET;

interface SearchApiAPI {

    @GET("/api/v1/search")
    @Headers({"Content-Type: application/json"})
    Call<JsonObject> search(@Query("api_key") String apiKey, @Query("engine") String engine, @Query("q") String q, @QueryMap Map<String, Object> paramsMap);
}

