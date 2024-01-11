package dev.langchain4j.model.wenxin.client;

import retrofit2.Response;
import java.io.IOException;

public class Utils {
    Utils() {
    }

    static RuntimeException toException(Response<?> response) throws IOException {
        return new BaiduHttpException(response.code(), response.errorBody().string());
    }

    static RuntimeException toException(okhttp3.Response response) throws IOException {
        return new BaiduHttpException(response.code(), response.body().string());
    }
}