package dev.langchain4j.store.embedding.oracle;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleConnectionWrapper;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * A DataSource implementation used by tests in this module. A simplistic connection pool is implemented so that
 * {@link #getConnection()} will return the same connection on successive calls. This allows tests to complete much
 * faster than they would if a new connection was created on each call. More sophisticated connection pools are readily
 * available, but using this pool will avoid introducing a new dependency. Not introducing dependencies is one of the
 * guidelines listed in the CONTRIBUTING.md document.
 */
public class TestDataSource implements DataSource {

    // static OracleContainer ORACLE_CONTAINER = new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart");

    private final DataSource dataSource;

    private final BlockingQueue<SharedConnection> pooledConnections = new ArrayBlockingQueue<>(1);

    public TestDataSource(DataSource dataSource) throws SQLException {
        this.dataSource = dataSource;

        OracleConnection connection = dataSource.getConnection().unwrap(OracleConnection.class);
        try {
            pooledConnections.put(new SharedConnection(connection));
        }
        catch (InterruptedException interruptedException) {
            throw new CompletionException(interruptedException);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            int timeout = dataSource.getLoginTimeout();

            if (timeout == 0)
                timeout = 30;

            Connection connection = pooledConnections.poll(timeout, TimeUnit.SECONDS);

            if (connection == null)
                throw new SQLTimeoutException("Timeout expired while waiting for a database connection");

            return connection;
        }
        catch (InterruptedException interruptedException) {
            throw new CompletionException(interruptedException);
        }
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return dataSource.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        dataSource.setLogWriter(out);
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return iface.isInstance(this) ? iface.cast(this) : dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || dataSource.isWrapperFor(iface);
    }

    private final class SharedConnection extends OracleConnectionWrapper {

        private SharedConnection(OracleConnection connection) {
            super(connection);
        }

        @Override
        public void close() {
            try {
                pooledConnections.put(this);
            }
            catch (InterruptedException interruptedException) {
                throw new CompletionException(interruptedException);
            }
        }
    }
}
