package dev.langchain4j.web.search.searchapi;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.QueryMap;

import java.util.Map;

interface SearchApiGoogleSearchApi {

    @GET("/api/v1/search")
    Call<SearchApiGoogleSearchResponse> search(@QueryMap Map<String, Object> params,
                        @Header("Authorization") String bearerToken);
}
