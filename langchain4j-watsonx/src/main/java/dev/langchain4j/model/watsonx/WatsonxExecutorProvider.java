package dev.langchain4j.model.watsonx;

import com.ibm.watsonx.ai.core.spi.executor.IOExecutorProvider;
import dev.langchain4j.Internal;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@Internal
public class WatsonxExecutorProvider implements IOExecutorProvider {

    @Override
    public Executor executor() {
        return ForkJoinPool.commonPool();
    }
}
