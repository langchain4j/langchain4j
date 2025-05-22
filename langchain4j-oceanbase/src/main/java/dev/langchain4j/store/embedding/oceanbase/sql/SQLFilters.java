package dev.langchain4j.store.embedding.oceanbase.sql;

/**
 * Factory class for creating SQL filter instances.
 */
public class SQLFilters {
    
    /**
     * Creates a SQLFilter that matches all rows.
     * 
     * @return A SQLFilter that matches all rows
     */
    public static SQLFilter matchAllRows() {
        return new MatchAllSQLFilter();
    }
    
    /**
     * Creates a SQLFilter that matches no rows.
     * 
     * @return A SQLFilter that matches no rows
     */
    public static SQLFilter matchNoRows() {
        return new MatchNoSQLFilter();
    }
    
    /**
     * Creates a SQLFilter with a simple SQL expression.
     * 
     * @param sql The SQL expression
     * @return A SQLFilter with the given SQL expression
     */
    public static SQLFilter simple(String sql) {
        return new SimpleSQLFilter(sql);
    }
    
    /**
     * Implementation of SQLFilter that matches all rows.
     */
    public static class MatchAllSQLFilter implements SQLFilter {
        @Override
        public String toSql() {
            return "1=1";
        }
        
        @Override
        public boolean matchesAllRows() {
            return true;
        }
        
        @Override
        public boolean matchesNoRows() {
            return false;
        }
    }
    
    /**
     * Implementation of SQLFilter that matches no rows.
     */
    public static class MatchNoSQLFilter implements SQLFilter {
        @Override
        public String toSql() {
            return "1=0";
        }
        
        @Override
        public boolean matchesAllRows() {
            return false;
        }
        
        @Override
        public boolean matchesNoRows() {
            return true;
        }
    }
    
    /**
     * Implementation of SQLFilter with a simple SQL expression.
     */
    public static class SimpleSQLFilter implements SQLFilter {
        private final String sql;
        
        /**
         * Creates a new SimpleSQLFilter with the given SQL expression.
         * 
         * @param sql The SQL expression
         */
        public SimpleSQLFilter(String sql) {
            this.sql = sql;
        }
        
        @Override
        public String toSql() {
            return sql;
        }
        
        @Override
        public boolean matchesAllRows() {
            return "1=1".equals(sql);
        }
        
        @Override
        public boolean matchesNoRows() {
            return "1=0".equals(sql);
        }
    }
}
