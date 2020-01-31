/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
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
package ssg.lib.api.dbms;

import ssg.lib.api.util.JDBCTools;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.api.API;
import ssg.lib.api.APICallable;
import ssg.lib.api.util.APIException;
import static ssg.lib.api.APIParameterDirection.in_out;
import static ssg.lib.api.APIParameterDirection.ret;
import ssg.lib.api.APIFunction;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.dbms.DB_API.DB_Adapter;
import ssg.lib.api.util.APITools;

/**
 *
 * @author sesidoro
 */
public class DB_API extends API {

    private static final long serialVersionUID = 1L;

    // connection-specific configuration
    transient DB_Adapter da;
    transient DB_Connectable dbConnectable;

    public DB_API(String db) {
        super(db);
    }

    public DB_Connectable getConnectable() {
        return dbConnectable;
    }

    public void setConnectable(DB_Connectable dbConnectable) {
        this.dbConnectable = dbConnectable;
        DB_Adapter da = createDA();
        if (da != null) {
            this.da = da;
        }
    }

    public DB_Adapter createDA() {
        return new JDBC_DA();
    }

    @Override
    public <T extends APICallable> T createCallable(APIProcedure proc, Object context) {
        return (T) new DB_Callable(proc);
    }

    public class DB_Callable implements APICallable {

        APIProcedure proc;
        List<CallableStatement> css = Collections.synchronizedList(new ArrayList<>());
        String sql;
        List<String> paramNames = new ArrayList<>();
        Map<String, DB_Type> in;
        Map<String, DB_Type> out;
        DB_Type resp;

        public DB_Callable(APIProcedure proc) {
            this.proc = proc;
            sql = proc.fqn();
            for (Entry<String, APIParameter> pe : proc.params.entrySet()) {
                APIParameter p = pe.getValue();
                try {
                    switch (p.direction) {
                        case in:
                            if (p.name != null) {
                                if (in == null) {
                                    in = new LinkedHashMap<>();
                                }
                                in.put(p.name, (DB_Type) p.type);
                                paramNames.add(p.name);
                            }
                            break;
                        case in_out:
                            if (p.name != null) {
                                if (in == null) {
                                    in = new LinkedHashMap<>();
                                }
                                in.put(p.name, (DB_Type) p.type);
                            }
                        case out:
                            if (out == null) {
                                out = new LinkedHashMap<>();
                            }
                            out.put(p.name, (DB_Type) p.type);
                            paramNames.add(p.name);
                            break;
                        case ret:
                            resp = (DB_Type) p.type;
                            break;
                        default:
                            throw new RuntimeException("Unsupported parameter type: " + p);
                    }
                } catch (Throwable th) {
                    throw new RuntimeException("Unsupported parameter type: " + p, th);
                }
            }
            if (proc instanceof APIFunction) {
                APIFunction dbf = (APIFunction) proc;
                if (dbf.response != null) {
                    resp = (DB_Type) dbf.response.type;
                } else {
                    System.out.println("*** NO RESPONSE TYPE IN FUNCTION");
                    int a = 0;
                }
            }

            // prepare JDBC-friendly SQL statement text
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            if (resp != null) {
                sb.append("?=");
            }
            sb.append("call ");
            sb.append(sql);
            sb.append('(');
            if (!paramNames.isEmpty()) {
                for (int i = 0; i < paramNames.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append('?');
                }
            }
            sb.append(")}");
            sql = sb.toString();
        }

        @Override
        public <T extends APIProcedure> T getAPIProcedure(Object params) {
            float test = APITools.testParameters(proc, params);
            return (test != 0 || params == null) ? (T) proc : null;
        }

        CallableStatement getCS(Connection connection) throws SQLException {
            CallableStatement cs = null;
            if (css.isEmpty()) {
                if (connection != null) {
                    cs = connection.prepareCall(sql);
                }
            } else {
                cs = css.remove(0);
                if (cs.getConnection().isClosed()) {
                    cs = null;
                    if (connection != null && !connection.isClosed()) {
                        cs = getCS(connection);
                    }
                }
            }
            return cs;
        }

        void ungetCS(CallableStatement cs) {
            if (cs != null) {
                try {
                    cs.close();
                } catch (SQLException sex) {
                    int a = 0;
                }
                //css.add(cs);
            }
        }

        @Override
        public <T> T call(Map<String, Object> params) throws APIException {
            DBResult r = new DBResult();

            Connection connection = getConnectable().getConnection(1000);
            CallableStatement cs = null;
            try {
                cs = getCS(connection);
                if (cs == null) {
                    throw new APIException("Failed to obtain callable statement for '" + sql + "': DB connection: " + connection);
                }
                if (resp != null) {
                    registerOutParameter(cs, 1, resp.getSQLType(), (resp instanceof DB_SQLType) ? null : resp.fqn());
                }
                if (!paramNames.isEmpty()) {
                    int base = (resp != null) ? 2 : 1;
                    for (int pOrder = base; pOrder < paramNames.size() + base; pOrder++) {
                        String n = paramNames.get(pOrder - base);
                        DB_Type pIn = (in != null) ? in.get(n) : null;
                        DB_Type pOut = (out != null) ? out.get(n) : null;
                        if (params != null && params.containsKey(n)) {
                            DB_Type t = pIn;
                            Object v = params.get(n);
                            v = da.toDB(t, v);
                            setInParameter(cs, t, pOrder, v);
                        } else {
                            if (pIn != null) {
                                setInParameter(cs, pIn, pOrder, null);
                            }
                        }
                        if (pOut != null) {
                            registerOutParameter(cs, pOrder, pOut.getSQLType(), (pOut instanceof DB_SQLType) ? null : pOut.fqn());
                        }
                    }
                }

                // do the call
                Object rs = executeCS(cs, false);//resp != null);
                if (rs instanceof ResultSet) {
                    r.put("_", JDBCTools.rs2list((ResultSet) rs, true));
                    ((ResultSet) rs).close();
                } else if (rs != null) {
                    r.put("_", da.fromDB(resp, rs));
                }

                // fetch out parameters
                if (out != null && !out.isEmpty()) {
                    for (int pOrder = 1; pOrder <= paramNames.size(); pOrder++) {
                        String n = paramNames.get(pOrder - 1);
                        DB_Type pOut = out.get(n);
                        if (pOut != null) {
                            Object v = getOutParameter(cs, pOut, pOrder);
                            r.put(n, da.fromDB(pOut, v));
                        }
                    }
                }

                return (T) r;
            } catch (IOException | SQLException ioex) {
                throw new APIException(ioex);
            } finally {
                if (cs != null) {
                    ungetCS(cs);
                }
                if (connection != null) {
                    getConnectable().ungetConnection(connection);
                }
            }
        }

        public void setInParameter(CallableStatement cs, DB_Type t, int pOrder, Object v) throws SQLException {
            //System.out.println("setIn: " + pOrder + " -> " + t + " -> " + ("" + v).replace("\n", "\\n").replace("\r", "\\r"));
            if (v == null) {
                cs.setNull(pOrder, t.getSQLType());
            } else if (t instanceof DB_SQLType) {
                String tn = ((DB_SQLType) t).name.toUpperCase();
                if ("VARCHAR2".equals(tn)) {
                    cs.setString(pOrder, (String) v);
                } else if (v instanceof BigDecimal) {
                    cs.setBigDecimal(pOrder, (BigDecimal) v);
                } else if (v instanceof Double) {
                    cs.setDouble(pOrder, (Double) v);
                } else if (v instanceof Float) {
                    cs.setFloat(pOrder, (Float) v);
                } else if (v instanceof Long) {
                    cs.setLong(pOrder, (Long) v);
                } else if (v instanceof Integer) {
                    cs.setInt(pOrder, (Integer) v);
                } else if (v instanceof Short) {
                    cs.setShort(pOrder, (Short) v);
                } else if (v instanceof Byte) {
                    cs.setByte(pOrder, (Byte) v);
                } else if (v instanceof java.sql.Date) {
                    cs.setDate(pOrder, (java.sql.Date) v);
                } else if (v instanceof Timestamp) {
                    cs.setTimestamp(pOrder, (Timestamp) v);
                } else if (v instanceof Boolean) {
                    cs.setBoolean(pOrder, (Boolean) v);
                } else {
                    throw new SQLException("Unsupported SQL data type: " + v);
                }
            } else if (t instanceof DB_CollectionType) {
                cs.setObject(pOrder, v);
            } else if (t instanceof DB_ObjectType) {
                cs.setObject(pOrder, v);
            } else {
                throw new SQLException("Unsupported SQL type: " + t);
            }
        }

        public void registerOutParameter(CallableStatement cs, int pOrder, int type, String typeName) throws SQLException {
            //System.out.println("registerOut: " + pOrder + " -> " + type);
            if (2002 == type) {
                int a = 0;
            }
            if (typeName != null) {
                cs.registerOutParameter(pOrder, type, typeName);
            } else {
                cs.registerOutParameter(pOrder, type);
            }
        }

        public Object getOutParameter(CallableStatement cs, DB_Type type, int pOrder) throws SQLException {
            return cs.getObject(pOrder);
        }

        public Object executeCS(CallableStatement cs, boolean withResultSet) throws SQLException {
            if (withResultSet) {
                ResultSet rs = cs.executeQuery();
                return rs;
            } else {
                cs.execute();
                if (resp != null) {
                    return this.getOutParameter(cs, resp, 1);
                } else {
                    return null;
                }
            }
        }

        @Override
        public String toString() {
            return "DB_Calleble{" + "sql=" + sql + ((in != null) ? ", in=" + in.keySet() : "") + ((out != null) ? ", out=" + out.keySet() : "") + ((resp != null) ? ", resp=" + resp.fqn() : "") + '}';
        }
    }

    public static class DBResult extends LinkedHashMap<String, Object> {

        public DBResult add(String name, Object value) {
            put(name, value);
            return this;
        }
    }

    /**
     * DB_Adapter converts type/value info to/from DB format. Used for interface
     * definition and actual data transfer
     */
    public static interface DB_Adapter {

        <T> T toDB(DB_Type type, Object value) throws IOException;

        <T> T fromDB(DB_Type type, Object value) throws IOException;
    }

    public class JDBC_DA implements DB_Adapter {

        Map<DB_Type, DB_Adapter> typeAdapters = Collections.synchronizedMap(new LinkedHashMap<>());
        DBVarchar2DA textDA = new DBVarchar2DA();
        DBNumberDA numberDA = new DBNumberDA();
        DBDateDA dateDA = new DBDateDA();
        //DBObjectDA objectDA = new DBObjectDA();

        public DB_Adapter getDA(DB_Type type) throws IOException {
            DB_Adapter da = typeAdapters.get(type);
            if (da == null) {
                da = createDA(type);
                if (da != null) {
                    typeAdapters.put(type, da);
                }
            }
            return da;
        }

        public DB_Adapter createDA(DB_Type type) throws IOException {
            if (type instanceof DB_SQLType) {
                // generic SQL type -> simple type adapter
                if ("VARCHAR2".equalsIgnoreCase(((DB_SQLType) type).name)) {
                    return textDA;
                } else if ("NUMBER".equalsIgnoreCase(((DB_SQLType) type).name)) {
                    return numberDA;
                } else if ("DATE".equalsIgnoreCase(((DB_SQLType) type).name)) {
                    return dateDA;
                } else {
                    // unsupported type -> ???
                    int a = 0;
                    throw new IOException("Type is not supported: " + type);
                }
            } else if (type instanceof DB_CollectionType) {
                // collection -> item type is needed...
                DB_CollectionType dbc = (DB_CollectionType) type;
                DB_Adapter itemDA = getDA((DB_Type) dbc.itemType);
                return new DBCollectionDA(itemDA);
//            } else if (type instanceof DB_ObjectType) {
//                return objectDA;
            } else {
                // unrecognized type -> ???
                int a = 0;
                throw new IOException("Unrecognized type: " + type);
            }
        }

        @Override
        public <T> T toDB(DB_Type type, Object value) throws IOException {
            DB_Adapter da = getDA(type);
            return da.toDB(type, value);
        }

        @Override
        public <T> T fromDB(DB_Type type, Object value) throws IOException {
            DB_Adapter da = getDA(type);
            return da.fromDB(type, value);
        }

        public class DBVarchar2DA implements DB_Adapter {

            @Override
            public <T> T toDB(DB_Type type, Object value) throws IOException {
                return (value != null) ? (T) value.toString() : null;
            }

            @Override
            public <T> T fromDB(DB_Type type, Object value) throws IOException {
                return (value != null) ? (T) value.toString() : null;
            }
        }

        public class DBNumberDA implements DB_Adapter {

            @Override
            public <T> T toDB(DB_Type type, Object value) throws IOException {
                return (value instanceof Number) ? (T) value : null;
            }

            @Override
            public <T> T fromDB(DB_Type type, Object value) throws IOException {
                return (value instanceof Number) ? (T) value : null;
            }
        }

        public class DBDateDA implements DB_Adapter {

            @Override
            public <T> T toDB(DB_Type type, Object value) throws IOException {
                return (value instanceof java.sql.Date) ? (T) value : (value instanceof java.util.Date) ? (T) new java.sql.Date(((java.util.Date) value).getTime()) : null;
            }

            @Override
            public <T> T fromDB(DB_Type type, Object value) throws IOException {
                return (value instanceof java.util.Date) ? (T) value : (value instanceof java.sql.Date) ? (T) new java.sql.Date(((java.sql.Date) value).getTime()) : null;
            }
        }

        public class DBCollectionDA implements DB_Adapter {

            DB_Adapter itemDA;

            public DBCollectionDA(DB_Adapter itemDA) {
                this.itemDA = itemDA;
            }

            @Override
            public <T> T toDB(DB_Type type, Object value) throws IOException {
                if (value == null) {
                    return null;
                } else if (value instanceof Collection) {
                    List lst = new ArrayList();
                    for (Object val : (Collection) value) {
                        lst.add(itemDA.toDB(type, val));
                    }
                    return (T) lst;
                } else if (value.getClass().isArray()) {
                    List lst = new ArrayList();
                    for (int i = 0; i < Array.getLength(value); i++) {
                        lst.add(itemDA.toDB(type, Array.get(value, i)));
                    }
                    return (T) lst;
                } else {
                    // cannot convert toDB list non-listable ???
                    throw new IOException("Cannot convert to DB list: " + value);
                }
            }

            @Override
            public <T> T fromDB(DB_Type type, Object value) throws IOException {
                if (value == null) {
                    return null;
                } else if (value instanceof Collection) {
                    List lst = new ArrayList();
                    for (Object val : (Collection) value) {
                        lst.add(itemDA.fromDB(type, val));
                    }
                    return (T) lst;
                } else if (value.getClass().isArray()) {
                    List lst = new ArrayList();
                    for (int i = 0; i < Array.getLength(value); i++) {
                        lst.add(itemDA.fromDB(type, Array.get(value, i)));
                    }
                    return (T) lst;
                } else {
                    // cannot convert toDB list non-listable ???
                    throw new IOException("Cannot convert to list from DB: " + value);
                }
            }
        }
    }
}
