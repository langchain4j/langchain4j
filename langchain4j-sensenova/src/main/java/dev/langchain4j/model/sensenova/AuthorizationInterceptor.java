package dev.langchain4j.model.sensenova;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;


import java.io.IOException;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;


class AuthorizationInterceptor implements Interceptor {

	private static final long expireMillis = 1000 * 60 * 30;
	private static final long notBeforeMillis = 1000 * 5;

    private final String apiKeyId;
    private final String apiKeySecret;
	private final Cache<String, String> cache = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMillis(expireMillis))
			.build();


	public AuthorizationInterceptor(String apiKeyId, String apiKeySecret) {
		this.apiKeyId = apiKeyId;
        this.apiKeySecret = apiKeySecret;
	}

	@Override
	public Response intercept(Chain chain) throws IOException {
		String token = getOrDefault(cache.getIfPresent(this.apiKeyId), generateToken());
		Request request = chain.request()
				.newBuilder()
				.addHeader("Authorization", "Bearer " + token)
				.removeHeader("Accept")
				.build();
		return chain.proceed(request);
	}

	private String generateToken() {
		try {
			Date expiredAt = new Date(System.currentTimeMillis() + expireMillis);
			Date notBefore = new Date(System.currentTimeMillis() - notBeforeMillis);
			Algorithm algo = Algorithm.HMAC256(apiKeySecret);
			Map<String, Object> header = new HashMap<>(2);
			header.put("alg", "HS256");
			final String token = JWT.create()
					.withIssuer(apiKeyId)
					.withHeader(header)
					.withExpiresAt(expiredAt)
					.withNotBefore(notBefore)
					.sign(algo);
			cache.put(this.apiKeyId, token);
			return token;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
