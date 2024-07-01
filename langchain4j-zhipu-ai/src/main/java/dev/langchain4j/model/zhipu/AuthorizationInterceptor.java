package dev.langchain4j.model.zhipu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.MacAlgorithm;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;

class AuthorizationInterceptor implements Interceptor {

    private static final long expireMillis = 1000 * 60 * 30;
    private static final String id = "HS256";
    private static final String jcaName = "HmacSHA256";
    private static final MacAlgorithm macAlgorithm;

    static {
        try {
            //create a custom MacAlgorithm with a custom minKeyBitLength
            int minKeyBitLength = 128;
            Class<?> c = Class.forName("io.jsonwebtoken.impl.security.DefaultMacAlgorithm");
            Constructor<?> ctor = c.getDeclaredConstructor(String.class, String.class, int.class);
            ctor.setAccessible(true);
            macAlgorithm = (MacAlgorithm) ctor.newInstance(id, jcaName, minKeyBitLength);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private final String apiKey;
    private final Cache<String, String> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(expireMillis))
            .build();


    public AuthorizationInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = getOrDefault(cache.getIfPresent(this.apiKey), generateToken());
        Request request = chain.request()
                .newBuilder()
                .addHeader(AUTHORIZATION, "Bearer " + token)
                .removeHeader(ACCEPT)
                .build();
        return chain.proceed(request);
    }

    private String generateToken() throws JsonProcessingException {
        String[] apiKeyParts = this.apiKey.split("\\.");
        String keyId = apiKeyParts[0];
        String secret = apiKeyParts[1];
        Map<String, Object> payload = new HashMap<>(3);
        payload.put("api_key", keyId);
        payload.put("exp", currentTimeMillis() + expireMillis);
        payload.put("timestamp", currentTimeMillis());

        String token = Jwts.builder()
                .header()
                .add("alg", id)
                .add("sign_type", "SIGN")
                .and()
                .content(Json.toJson(payload))
                .signWith(new SecretKeySpec(secret.getBytes(UTF_8), jcaName), macAlgorithm)
                .compact();
        cache.put(this.apiKey, token);
        return token;
    }

}
