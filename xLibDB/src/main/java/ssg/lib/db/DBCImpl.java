/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.db;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import ssg.lib.common.CommonTools;

/**
 * 
 * @author 000ssg
 */
public class DBCImpl extends DBC {

    String driverName = "oracle.jdbc.OracleDriver";
    String pwd;

    List<Connection> connections = Collections.synchronizedList(new ArrayList<>());
    Map<Connection, Long> providedConnections = new HashMap<>();

    public DBCImpl(String url, String user, String pwd) throws SQLException {
        super(url, user);
        this.pwd = pwd;

        Connection conn = getConnection(1000 * 2);
        if (conn != null) {
            ungetConnection(conn);
        }
    }

    public DBCImpl(String url, String user, String pwd, DBStatistics stat) throws SQLException {
        super(url, user);
        this.pwd = pwd;
        this.stat = stat;

        Connection conn = getConnection(1000 * 2);
        if (conn != null) {
            ungetConnection(conn);
        }
    }

    public synchronized int availableConnections() {
        return connections.size();
    }

    public synchronized int connectionsCount() {
        int cs = connections.size();
        int pcs = providedConnections.size();
        return cs + pcs;
    }

    @Override
    public Connection getConnection(long timeout) {
        long started = System.nanoTime();
        if (stat != null) {
            stat.counters().onGet();
        }
        CommonTools.wait(timeout, () -> {
            int acs = availableConnections();
            int cs = connectionsCount();
            return acs == 0 && cs > 15;
        });
        Connection dbConnection = null;
        synchronized (connections) {
            dbConnection = (!connections.isEmpty()) ? connections.remove(0) : null;
        }
        if (dbConnection == null) {
            try {
                dbConnection = create();
                if (stat != null) {
                    stat.counters().onCreate();
                }
            } catch (SQLException sex) {
                sex.printStackTrace();
            }
        }
        synchronized (providedConnections) {
            if (stat != null) {
                stat.alloc().onDuration(System.nanoTime() - started);
            }
            providedConnections.put(dbConnection, System.nanoTime());
        }
        stat.counters().onGot();
        return dbConnection;
    }

    Connection create() throws SQLException {
        try {
            Driver driver = (Driver) Class.forName(driverName).newInstance();

            Properties connectionProperties = new Properties();
            if (user != null) {
                connectionProperties.put("user", user);
            }
            if (pwd != null) {
                connectionProperties.put("password", pwd);
            }

            Connection connection = driver.connect(url, connectionProperties);
            connection.setAutoCommit(false);
            return connection;
        } catch (Exception e) {
            throw new SQLException("Problem opening DB connection: " + e.getMessage());
        }
    }

    @Override
    public synchronized void ungetConnection(Connection connection) {
        if (connection != null) {
            connections.add(connection);
            Long started = providedConnections.remove(connection);
            if (started != null) {
                if (stat != null) {
                    stat.exec().onDuration(System.nanoTime() - started);
                }
            }
            if (stat != null) {
                stat.counters().onUnget();
            }
        }
    }

    @Override
    public void close() {
        for (Connection c : connections) {
            try {
                c.close();
            } catch (Throwable th) {
            }
        }
        connections.clear();
        for (Connection c : providedConnections.keySet()) {
            try {
                Long started = providedConnections.get(c);
                if (stat != null) {
                    stat.exec().onDuration(System.nanoTime() - started);
                }
                started = System.nanoTime();
                c.close();
                if (stat != null) {
                    stat.dealloc().onDuration(System.nanoTime() - started);
                }
            } catch (Throwable th) {
            }
        }
        providedConnections.clear();
    }
}
