package dev.langchain4j.web.search.searxng;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.web.search.WebSearchRequest;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SearXNGClient {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private SearXNGApi api;

	@Builder
	public SearXNGClient(Duration timeout, String baseUrl) {
		OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
				.callTimeout(timeout)
				.connectTimeout(timeout)
				.readTimeout(timeout)
				.writeTimeout(timeout);
		Retrofit retrofit = new Retrofit.Builder()
				.baseUrl(baseUrl)
				.client(okHttpClientBuilder.build())
				.addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
				.build();
		this.api = retrofit.create(SearXNGApi.class);
	}

	SearXNGResults search(WebSearchRequest request) {
		try {
			final Map<String, Object> args = new HashMap<>();
			args.put("q", request.searchTerms());
			args.put("format", "json");
			final Response<SearXNGResults> response = api.search(args).execute();
			return response.body();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		final SearXNGResults results = new SearXNGClient(Duration.ofSeconds(10l), "http://localhost:8080").search(WebSearchRequest.from("Neil Armstrong"));
		System.out.println(results);
	}
}
