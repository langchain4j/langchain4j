package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;

import java.lang.reflect.InvocationHandler;

public interface CognisphereOwner extends InvocationHandler  {
    CognisphereOwner withCognisphere(DefaultCognisphere cognisphere);
}
