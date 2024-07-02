package dev.langchain4j.web.search.searchapi;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

interface SearchApiAPI {

    @GET("/api/v1/search")
    @Headers({"Content-Type: application/json"})
    Call<SearchApiResponse> search(@Query("api_key") String apiKey, @Query("engine") String engine, @Query("q") String q, @QueryMap Map<String, Object> paramsMap);
}

