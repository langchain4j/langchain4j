package dev.langchain4j.web.search.brave;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface BraveApi {

    @GET("search")
    @Headers({
            "Accept: application/json",
            "X-Subscription-Token: "
    })
    Call<BraveResponse> search(
            @Query("q") String query,
            @Query("count") Integer count,
            @Query("safeSearch") String safeSearch,
            @Query("resultFilter") String resultFilter,
            @Query("freshness") String freshness
    );
}
