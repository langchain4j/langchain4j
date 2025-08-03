package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.cognisphere.CognisphereRegistry;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;

import java.lang.reflect.InvocationHandler;

@Internal
public interface CognisphereOwner {
    CognisphereOwner withCognisphere(DefaultCognisphere cognisphere);
    CognisphereRegistry registry();
}
