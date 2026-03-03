package org.example;

import java.sql.SQLException;

public class OracleChatMemoryStoreException extends RuntimeException{
    public OracleChatMemoryStoreException(String exception){
        super(exception);
    }
    public OracleChatMemoryStoreException (String exception, SQLException e){
        super(exception,e);
    }
}
