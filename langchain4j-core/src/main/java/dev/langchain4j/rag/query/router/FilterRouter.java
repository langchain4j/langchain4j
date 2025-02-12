package dev.langchain4j.rag.query.router;

/**
 * @Author dzy
 * @Date 2025/2/12 11:15
 * @PackageName:dev.langchain4j.rag.query.router
 * @ClassName: FilterRouter
 * @Description: TODO
 * @Version 1.0
 */
public interface FilterRouter {
    String doFilter(String response);
}
