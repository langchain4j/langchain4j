package org.example;

import oracle.jdbc.pool.OracleDataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

public class OracleWalletDataSourceFactory {
    public static DataSource createconnection() throws SQLException {
        PoolDataSource pool= PoolDataSourceFactory.getPoolDataSource();
        pool.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        pool.setURL(System.getenv("url"));
        pool.setUser(System.getenv("user"));
        pool.setPassword(System.getenv("password"));
        pool.setInitialPoolSize(1);
        pool.setMinPoolSize(1);
        pool.setMaxPoolSize(10);
        return pool;
    }
}
