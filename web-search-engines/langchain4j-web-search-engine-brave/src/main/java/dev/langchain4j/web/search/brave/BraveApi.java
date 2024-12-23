package dev.langchain4j.web.search.brave;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

import java.util.Map;

interface BraveApi {

    @GET("res/v1/web/search")
    @Headers({
            "Accept: application/json"
    })
    Call<BraveWebSearchResponse> search(
            @Header("X-Subscription-Token") String apiKey,
            @QueryMap Map<String,Object> params
    );
}
