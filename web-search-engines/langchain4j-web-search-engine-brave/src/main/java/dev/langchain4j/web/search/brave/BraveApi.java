package dev.langchain4j.web.search.brave;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface BraveApi {

    @GET("search")
    @Headers({
            "Accept: application/json"
    })
    Call<BraveResponse> search(
            @Header("X-Subscription-Token") String apiKey,
            @Query("q") String query,
            @Query("count") Integer count,
            @Query("safeSearch") String safeSearch,
            @Query("resultFilter") String resultFilter,
            @Query("freshness") String freshness
    );
}
