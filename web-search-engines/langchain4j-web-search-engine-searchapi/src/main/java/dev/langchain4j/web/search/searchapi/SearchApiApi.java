package dev.langchain4j.web.search.searchapi;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface SearchApiApi {

    @POST("/search")
    @Headers({"Content-Type: application/json"})
    Call<SearchApiResponse> search(@Body SearchApiSearchRequest request);
}

