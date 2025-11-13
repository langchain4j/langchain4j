package dev.langchain4j.model.watsonx;

import static java.util.Objects.isNull;

import com.ibm.watsonx.ai.core.spi.executor.IOExecutorProvider;
import dev.langchain4j.Internal;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Internal
public class WatsonxExecutorProvider implements IOExecutorProvider {

    private static Executor ioExecutor;

    @Override
    public synchronized Executor executor() {
        if (isNull(ioExecutor)) {

            ExecutorService defaultExecutor = Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                defaultExecutor.shutdown();
            }));

            ioExecutor = defaultExecutor;
        }
        return ioExecutor;
    }
}
