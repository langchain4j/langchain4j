package dev.langchain4j.web.search.searchapi;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.QueryMap;

import java.util.Map;

interface SearchApi {

    @GET("api/v1/search")
    @Headers("User-Agent: langchain4j-web-search-engine-searchapi")
    Call<SearchApiWebSearchResponse> search(@QueryMap Map<String, Object> params,
                                            @Header("Authorization") String bearerToken);
}
