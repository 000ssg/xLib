/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.httpapi_cs;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.api.API;
import ssg.lib.api.dbms.DB_API;
import ssg.lib.api.dbms.DB_API_Builder;
import ssg.lib.api.dbms.DB_API_Builder.DB_API_Context;
import ssg.lib.api.dbms.DB_API_Builder.DB_TypeResolver;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.api.util.Reflective_API_Builder.Reflective_API_Context;
import ssg.lib.common.Config;

/**
 * Provides generic configurable API adapter for easy initializaing APIs from
 * configurable parameters.
 *
 * @author 000ssg
 */
public class APIAdapter {

    public static final String API_TYPE_REFLECTION = "reflection";
    public static final String API_TYPE_JDBC = "jdbc";
    public static final String API_TYPE_JDBC_ORACLE = "oracle";

    Map<Object, Object> contexts = new LinkedHashMap<>();

    /**
     * Configures context for use with APIs.
     *
     * Use class, class name, or API name to register/find contexts.
     *
     * @param key
     * @param value
     * @return
     */
    public APIAdapter configureContext(Object key, Object value) {
        contexts.put(key, value);
        return this;
    }

    /**
     * Builds APIadapter configuration for text representation.
     *
     * Default syntax is ";"-eparated named parameters with optionally
     * ","-separated item values.
     *
     * @param api
     * @return
     */
    public APIAdapterConf createAPIAdapterConf(String text) {
        return new APIAdapterConf(text.startsWith("{") || text.startsWith("[") ? new String[]{text} : text.split(";"));
    }

    /**
     * Build API based on textual description in "'name'[='value';]"-separated
     * format with multiple items using ","-separated values.
     *
     * type=(reflection|jdbc|oracle);namespace;name;uri;authid;compact;item[];verifier
     *
     * @param api
     * @return
     */
    public API createAPI(String api) throws IOException {
        APIAdapterConf conf = new APIAdapterConf(api);
        return createAPI(conf);
    }

    public API createAPI(APIAdapterConf conf) throws IOException {
        try {
            if (API_TYPE_REFLECTION.equalsIgnoreCase(conf.type)) {
                return Reflective_API_Builder.buildAPI(conf.name, createBuilderContext(conf), prepareParameters(conf));
            } else if (API_TYPE_JDBC.equalsIgnoreCase(conf.type)) {
                return DB_API_Builder.buildJDBC(createBuilderContext(conf), prepareParameters(conf), createVerifier(conf));
            } else if (API_TYPE_JDBC_ORACLE.equalsIgnoreCase(conf.type)) {
                return DB_API_Builder.buildOracle(createBuilderContext(conf), prepareParameters(conf), createVerifier(conf));
            }
        } catch (Throwable th) {
            if (th instanceof IOException) {
                throw (IOException) th;
            } else {
                throw new IOException("Failed to build API " + conf.type + ", name=" + conf.name, th);
            }
        }
        throw new IOException("Unsupported/misconfigured API builder: " + conf.type + ", name=" + conf.name);
    }

    public <T> T createBuilderContext(APIAdapterConf conf) {
        try {
            if (API_TYPE_REFLECTION.equalsIgnoreCase(conf.type)) {
                return (T) new Reflective_API_Context()
                        .configure(new API_MethodsProvider_AccessHelper());
            } else if (API_TYPE_JDBC.equalsIgnoreCase(conf.type)) {
                return (T) new DB_API_Context(getDBAPI(conf), getConnection(conf), getTypeResolver(conf));
            } else if (API_TYPE_JDBC_ORACLE.equalsIgnoreCase(conf.type)) {
                return (T) new DB_API_Context(getDBAPI(conf), getConnection(conf), getTypeResolver(conf));
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public <T> T createVerifier(APIAdapterConf conf) {
        return null;
    }

    public <T> T prepareParameters(APIAdapterConf conf) {
        try {
            if (API_TYPE_REFLECTION.equalsIgnoreCase(conf.type)) {
                Class[] cls = new Class[conf.item.length];
                for (int i = 0; i < cls.length; i++) {
                    cls[i] = Class.forName(conf.item[i]);
                }
                return (T) cls;
            } else if (API_TYPE_JDBC.equalsIgnoreCase(conf.type)) {
                return (T) conf.item;
            } else if (API_TYPE_JDBC_ORACLE.equalsIgnoreCase(conf.type)) {
                return (T) conf.item;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public <T> T getContexts(APIAdapterConf conf) {
        try {
            if (API_TYPE_REFLECTION.equalsIgnoreCase(conf.type)) {
                List r = new ArrayList();
                Class[] cls = prepareParameters(conf);
                if (cls != null) {
                    for (int i = 0; i < cls.length; i++) {
                        Object o = contexts.get(cls[i]);
                        if (o == null) {
                            o = contexts.get(cls[i].getCanonicalName());
                        }
                        if (o == null) {
                            o = contexts.get(conf.name);
                        }
                        if (o == null) try {
                            o = cls[i].newInstance();
                            contexts.put(cls[i], o);
                        } catch (Throwable th) {
                            int a = 0;
                        }
                        if (o != null) {
                            r.add(o);
                        }
                    }
                }
                return (T) r;
            } else if (API_TYPE_JDBC.equalsIgnoreCase(conf.type)) {
                //return (T) conf.item;
            } else if (API_TYPE_JDBC_ORACLE.equalsIgnoreCase(conf.type)) {
                //return (T) conf.item;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    public DB_API getDBAPI(APIAdapterConf conf) {
        return null;
    }

    public Connection getConnection(APIAdapterConf conf) {
        return null;
    }

    public DB_TypeResolver getTypeResolver(APIAdapterConf conf) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("\n  contexts=" + contexts.size());
        for (Entry e : contexts.entrySet()) {
            sb.append("\n    " + e.getKey() + ": " + (e.getValue() != null ? e.getValue().getClass().getName() : "<none>"));
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    /**
     * Applies Config logic to ";" separated text
     */
    public static class APIAdapterConf extends Config {

        @Description("API builder type: reflection (default), jdbc, oracle...")
        public String type = "reflection";
        @Description("API namespace as prefix.")
        public String namespace = null;
        @Description("API name")
        public String name = null;
        @Description("API publication target")
        public String uri = null;
        @Description("User ID for API puvlication target")
        public String authid = null;
        @Description("User pwd for API publication target")
        public String pwd = null;
        @Description("API items: class names/aliases for reflection, DB schema names for DB API (jdbc or oracle)")
        public String[] item = null;
        @Description("API publication 'preficed' mode (i.e. publishing namespace, not each method).")
        public boolean prefixed = false;
        @Description("DB API verifier class")
        public String verifier = null;
        // data cacheing/exchange between calls like getDBAPI, getConnection, getTypeResolver...
        public transient Map<String, Object> properties = new LinkedHashMap<>();

        public APIAdapterConf() {
            super("");
            this.noSysProperties();
        }

        public APIAdapterConf(String... api) {
            super("");
            this.noSysProperties();
            Config.load(this, api);
        }

        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append("type=" + type);
            if (namespace != null) {
                sb.append(";namespace=" + namespace);
            }
            if (name != null) {
                sb.append(";name=" + name);
            }
            if (uri != null) {
                sb.append(";uri=" + uri);
            }
            if (authid != null) {
                sb.append(";authid=" + authid);
            }
            if (pwd != null) {
                sb.append(";pwd=" + pwd);
            }
            if (item != null && item.length > 0) {
                sb.append(";item=");
                for (int i = 0; i < item.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(item[i]);
                }
            }
            if (prefixed) {
                sb.append(";prefixed");
            }
            if (verifier != null) {
                sb.append(";verifier=" + verifier);
            }
            if (other() != null && !other().isEmpty()) {
                for (Entry<String, Object> e : other().entrySet()) {
                    sb.append(";" + e.getKey() + "=" + e.getValue());
                }
            }
            return sb.toString();
        }
    }

}
