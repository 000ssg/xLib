/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
package ssg.lib.api.wamp_cs.db;

import java.sql.SQLException;
import ssg.lib.api.dbms.DB_Connectable;

/**
 *
 * @author 000ssg
 */
public abstract class DBC implements DB_Connectable {

    String url;
    String user;

    DBStatistics stat = new DBStatistics();

    public DBC(String url, String user) throws SQLException {
        this.url = url;
        this.user = user;
    }

    public DBC(String url, String user,
            DBStatistics stat
    ) throws SQLException {
        this.url = url;
        this.user = user;
        if (stat != null) {
            this.stat = stat;
        }
    }

    public abstract int availableConnections();

    public abstract int connectionsCount();

    public abstract void close();

    public String getStat() {
        return (stat != null) ? stat.dumpStatistics(false) : "";
    }
}
