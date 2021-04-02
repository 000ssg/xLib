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
package ssg.lib.http_cs;

import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.common.Config;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.ReflectiveMethodsProvider;
import ssg.lib.http.rest.SpringBootWebMethodsProvider;
import ssg.lib.http.rest.WSDLMethodsProvider;
import ssg.lib.http.rest.XMethodsProvider;

/**
 *
 * @author sesidoro
 */
public class RESTAdapter {

    public Map<Object, Object> contexts = new LinkedHashMap<>();

    public RESTAdapter configureContext(Object key, Object value) {
        contexts.put(key, value);
        return this;
    }

    public RESTAdapterConf createRESTAdapterConf(String rest) {
        return new RESTAdapterConf("", rest.split(";"));
    }

    public MethodsProvider[] getMethodProviders(RESTAdapterConf conf) {
        if (conf == null || conf.type == null) {
            return null;
        }
        String[] ss = conf.type.split(",");
        for (int i = 0; i < ss.length; i++) {
            ss[i] = ss[i].trim();
        }
        MethodsProvider[] r = new MethodsProvider[ss.length];
        for (int i = 0; i < ss.length; i++) {
            if ("x".equalsIgnoreCase(ss[i])) {
                r[i] = new XMethodsProvider();
            } else if ("wsdl".equalsIgnoreCase(ss[i])) {
                r[i] = new WSDLMethodsProvider();
            } else if ("springboot".equalsIgnoreCase(ss[i])) {
                r[i] = new SpringBootWebMethodsProvider();
            } else if ("reflection".equalsIgnoreCase(ss[i])) {
                r[i] = new ReflectiveMethodsProvider();
            } else {
                try {
                    Class cl = Class.forName(ss[i]);
                    if (cl != null && MethodsProvider.class.isAssignableFrom(cl)) {
                        MethodsProvider mp = (MethodsProvider) cl.newInstance();
                        r[i] = mp;
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }
        return r;
    }

    public Object[] getProviders(RESTAdapterConf conf) {
        Object[] r = new Object[conf.item != null ? conf.item.length : 0];
        if (conf.item != null) {
            for (int i = 0; i < conf.item.length; i++) {
                try {
                    Object o = contexts.get(conf.item[i]);
                    if (o == null) {
                        Class cl = Class.forName(conf.item[i]);
                        o = contexts.get(cl);
                        if (o == null && !(cl.isInterface() )) {
                            o = cl.newInstance();
                        }
                    }
                    if (o != null) {
                        r[i] = o;
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }
        return r;
    }

    public static class RESTAdapterConf extends Config {

        public RESTAdapterConf() {
            super("");
            noSysProperties();

        }

        public RESTAdapterConf(String base, String... args) {
            super(base, args);
            noSysProperties();
        }

        @Description("REST methods provider: x,wsdl,springboot,reflection or class name (implementing MethodsProvider)")
        public String type;
        @Description("REST path prefix")
        public String name;
        @Description("Item (class name or alias name), multiple items allowwd separated with ','.")
        public String[] item;
    }
}
