package dev.langchain4j.model.typesafe.internal;

import dev.langchain4j.Internal;
import java.util.Map;

@Internal
public class TypeSafeRequest {

    public String model;
    public Object state;
    public Map<String, TypeSafeQuestion> questions;
}
