package dev.langchain4j.web.search.brave;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface BraveApi {

    @POST("search")
    @Headers({"Content-Type: application/json","X-Subscription-Token:BSAJsGw6fk3ePFkR_dBzlsY7UYovyDa"})
    Call<BraveResponse> search(@Body BraveWebSearchRequest request);

}
