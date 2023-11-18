import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

class AuthorizationHeaderInjector implements Interceptor {
    private final String apiKey;

    AuthorizationHeaderInjector(String apiKey) {
        this.apiKey = apiKey;
    }

    @NotNull
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request().newBuilder().addHeader("Authorization", "Bearer " + this.apiKey).build();
        return chain.proceed(request);
    }
}
