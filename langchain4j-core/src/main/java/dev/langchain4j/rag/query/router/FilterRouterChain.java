package dev.langchain4j.rag.query.router;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author dzy
 * @Date 2025/2/12 11:43
 * @PackageName:dev.langchain4j.rag.query.router
 * @ClassName: FilterRouterChain
 * @Description: TODO
 * @Version 1.0
 */
public class FilterRouterChain {
    private List<FilterRouter> filterRouters = new CopyOnWriteArrayList<>();

    public List<FilterRouter> getFilterRouters() {
        return filterRouters;
    }

    public FilterRouterChain addFilterRouter(FilterRouter filterRouter) {
        filterRouters.add(filterRouter);
        return this;
    }

    public String doFilter(String msg) {
        String returnMsg = msg;
        for(FilterRouter filter : filterRouters){
            returnMsg = filter.doFilter(returnMsg);
        }
        return returnMsg;
    }
}
