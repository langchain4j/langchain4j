package dev.langchain4j.rag.query.router;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FilterRouterChain {
    private List<FilterRouter> filterRouters = new CopyOnWriteArrayList<>();

    public List<FilterRouter> getFilterRouters() {
        return filterRouters;
    }

    public FilterRouterChain addFilterRouter(FilterRouter filterRouter) {
        filterRouters.add(filterRouter);
        sortFilters();
        return this;
    }

    public void addFilters(List<FilterRouter> filters) {
        this.filterRouters.addAll(filters);
        sortFilters();
    }

    private void sortFilters() {
        Collections.sort(filterRouters, Comparator.comparingInt(FilterRouter::getOrder));
    }

    public String doFilter(String msg) {
        String returnMsg = msg;
        for (FilterRouter filter : filterRouters) {
            returnMsg = filter.doFilter(returnMsg);
        }
        return returnMsg;
    }
}
