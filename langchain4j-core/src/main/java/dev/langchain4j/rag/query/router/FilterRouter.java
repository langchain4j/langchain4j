package dev.langchain4j.rag.query.router;

public interface FilterRouter {

    String doFilter(String response);

    int getOrder();
}
