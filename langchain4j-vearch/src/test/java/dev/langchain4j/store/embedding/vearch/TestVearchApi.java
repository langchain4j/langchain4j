package dev.langchain4j.store.embedding.vearch;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Path;

public interface TestVearchApi {

    @DELETE("/space/{db}/{space}")
    Call<Void> deleteSpace(@Path("db") String dbName, @Path("space") String spaceName);
}
