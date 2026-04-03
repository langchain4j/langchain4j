package dev.langchain4j.model.watsonx.utils;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import org.mockito.ArgumentCaptor;

public class HttpUtils {

    public static String bodyPublisherToString(ArgumentCaptor<HttpRequest> request) {
        HttpRequest.BodyPublisher bodyPublisher =
                request.getValue().bodyPublisher().orElseThrow();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bodyPublisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                baos.write(item.array(), item.position(), item.remaining());
            }

            @Override
            public void onError(Throwable throwable) {
                throw new RuntimeException(throwable);
            }

            @Override
            public void onComplete() {}
        });

        return baos.toString(StandardCharsets.UTF_8);
    }
}
