package dev.langchain4j.mcp.client.transport.docker;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DockerResultCallback extends ResultCallback.Adapter<Frame> {
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    @Override
    public void onNext(Frame item) {
        countDownLatch.countDown();
    }

    @Override
    public Adapter<Frame> awaitCompletion() throws InterruptedException {
        countDownLatch.await();
        return this;
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        return countDownLatch.await(timeout, timeUnit);
    }
}
