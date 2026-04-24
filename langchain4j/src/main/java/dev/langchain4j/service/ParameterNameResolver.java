package dev.langchain4j.service;

import java.lang.reflect.Parameter;
import java.util.ServiceLoader;

public interface ParameterNameResolver {
    boolean hasVariableName(Parameter parameter);
    String getVariableName(Parameter parameter);

    static String name(Parameter parameter) {
        return Holder.RESOLVER.getVariableName(parameter);
    }

    static boolean hasName(Parameter parameter) {
        return Holder.RESOLVER.hasVariableName(parameter);
    }

    class Holder {
        public static final ParameterNameResolver RESOLVER = findResolver();

        private Holder() { }

        static ParameterNameResolver findResolver() {
            for (ParameterNameResolver resolver : ServiceLoader.load(ParameterNameResolver.class)) {
                return resolver;
            }
            return new DefaultParameterNameResolver();

        }
    }

    class DefaultParameterNameResolver implements ParameterNameResolver {
        @Override
        public boolean hasVariableName(final Parameter parameter) {
            return parameter.getAnnotation(V.class) != null;
        }

        @Override
        public String getVariableName(Parameter parameter) {
            V annotation = parameter.getAnnotation(V.class);
            if (annotation != null) {
                return annotation.value();
            } else {
                return parameter.getName();
            }
        }
    }
}
