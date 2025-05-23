package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.cognisphere.Cognisphere;

import java.lang.reflect.InvocationHandler;

public interface CognisphereOwner extends InvocationHandler  {
    CognisphereOwner withCognisphere(Cognisphere cognisphere);
}
