package dev.langchain4j.model.sparkdesk;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

class AuthorizationInterceptor implements Interceptor {

    private final String apiKey;
    private final String apiSecret;


    public AuthorizationInterceptor(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        HttpUrl url = chain.request().url();
        String host = url.host();
        Request request = null;
        switch (host) {
            case "spark-api-open.xf-yun.com": {
                request = chain.request()
                        .newBuilder()
                        .addHeader("Authorization", "Bearer " + apiKey + ":" + apiSecret)
                        .build();
                break;
            }
            case "spark-api.xf-yun.com": {
                request = chain.request()
                        .newBuilder()
                        .url(authUrl(url.toString(), apiKey, apiSecret, "GET")
                                .replace("/?", "?")
                                .replace("http://", "ws://")
                                .replace("https://", "wss://"))
                        .build();
                break;
            }
            case "emb-cn-huabei-1.xf-yun.com":
            case "spark-api.cn-huabei-1.xf-yun.com": {
                request = chain.request()
                        .newBuilder()
                        .url(authUrl(url.toString(), apiKey, apiSecret, "POST"))
                        .build();
                break;
            }
            default: {
                request = chain.request();
            }
        }
        return chain.proceed(request);
    }


    private static String authUrl(String hostUrl, String appKey, String appSecret, String httpMethod) {
        try {
            URL url = new URL(hostUrl);

            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new Date());

            String path = url.getPath();
            path = path.equals("/") ? path : path.endsWith("/") ? path.substring(0, url.getPath().length() - 1) : path;
            String preStr = "host: " + url.getHost() + "\n" +
                    "date: " + date + "\n" +
                    httpMethod + " " + path + " HTTP/1.1";

            Mac mac = Mac.getInstance("hmacsha256");
            SecretKeySpec spec = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
            mac.init(spec);

            byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
            String sha = Base64.getEncoder().encodeToString(hexDigits);

            String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", appKey, "hmac-sha256", "host date request-line", sha);
            HttpUrl httpUrl = Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath())).newBuilder().
                    addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))).
                    addQueryParameter("date", date).
                    addQueryParameter("host", url.getHost()).
                    build();

            return httpUrl.toString();
        } catch (Exception e) {
            throw illegalArgument(e.getMessage());
        }
    }
}
