package dev.langchain4j.llama3;

@FunctionalInterface
public interface Sampler {
    int sampleToken(FloatTensor logits);

    Sampler ARGMAX = FloatTensor::argmax;
}
