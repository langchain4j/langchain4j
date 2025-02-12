package dev.langchain4j.rag.query.router;

/**
 * @Author dzy
 * @Date 2025/2/12 14:01
 * @PackageName:dev.langchain4j.rag.query.router
 * @ClassName: DeepSeekFilter
 * @Description: TODO
 * @Version 1.0
 */
public class DeepSeekFilter implements FilterRouter{
    @Override
    public String doFilter(String response) {
        return response.replaceAll("(?si)<think\\b[^>]*>.*?</think>", "");
    }
}
