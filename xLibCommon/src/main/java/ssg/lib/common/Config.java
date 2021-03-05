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
package ssg.lib.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import ssg.lib.common.Refl.ReflImpl;

/**
 * Retrieves configuration parameters from system properties and (overriding
 * those) from args as name=value pairs.
 *
 * Parameters are assigned either to public fields or in map based on "base"
 * property. Property names are "." separated (assuming starting as base+"."),
 * dots are replaced with "_" and finally reduced/harmonized name is compared to
 * field name or is stored as "other.
 *
 * Config.load ecognizes nested config sources via "configFile=..." and
 * "configURL=..." properties. NOTE: in system properties these arguments are
 * ignored.
 *
 * @author sesidoro
 */
public class Config {

    static Refl refl = new ReflImpl();
    private String base;
    private Map<String, Object> other;
    private boolean sysPropsLoaded = false;

    public Config(String base, String... args) {
        this.base = base != null ? base : getClass().isAnonymousClass() ? getClass().getSuperclass().getName() : getClass().getSimpleName();
        load(this, args);
    }

    public String getBase() {
        return base;
    }

    public <T> T get(String name) {
        Object obj = null;
        for (Field f : getClass().getFields()) {
            if (f.getName().equals(name)) {
                try {
                    return (T) f.get(this);
                } catch (Throwable th) {
                    return null;
                }
            }
        }
        return other != null ? (T) other.get(name) : null;
    }

    public Map<String, Object> other() {
        return other != null ? other : Collections.emptyMap();
    }

    public Map<String, Object> toMap(boolean includeAll) {
        Map<String, Object> r = new LinkedHashMap<>();
        Field[] fs = getClass().getFields();
        for (Field f : fs) {
            try {
                Object v = f.get(this);
                if (v == null || v instanceof String && ((String) v).isEmpty()) {
                    continue;
                }
                r.put(f.getName(), v);
            } catch (Throwable th) {
            }
        }
        if (includeAll & other != null) {
            r.putAll(other);
        }
        return r;
    }

    public static void load(Config config, String... args) {
        String base = config.getBase() + ".";
        Field[] fs = config.getClass().getFields();
        Map<String, Field> fm = new HashMap<>();
        for (Field f : fs) {
            fm.put(f.getName(), f);
        }
        Map<String, List> props = new HashMap<>();
        if (!config.sysPropsLoaded) {
            Collection<URL> configSources = new ArrayList<>();
            for (String pn : System.getProperties().stringPropertyNames()) {
                String v = System.getProperty(pn);
                URL url = null;
                try {
                    if ("configFile".equals(pn)) {
                        url = new File(v).toURI().toURL();
                    } else if ("configURL".equals(pn)) {
                        url = new URL(v);
                    } else {
                        List l = props.get(pn);
                        if (l == null) {
                            l = new ArrayList();
                            props.put(pn, l);
                        }
                        l.add(v);
                    }
                } catch (Throwable th) {
                }
                if (url != null) {
                    configSources.add(url);
                }
            }
            for (URL url : configSources) {
                if (url != null) {
                    try ( InputStream is = url.openStream();) {
                        Properties ps = new Properties();
                        ps.load(url.openStream());
                        for (Entry e : ps.entrySet()) {
                            List l = props.get(e.getKey().toString());
                            if (l == null) {
                                l = new ArrayList();
                                props.put(e.getKey().toString(), l);
                            }
                            l.add(e.getValue());
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
            config.sysPropsLoaded = true;
        }
        if (args != null) {
            for (String s : args) {
                if (s == null || !s.contains("=")) {
                    continue;
                }
                int idx = s.indexOf("=");
                String n = s.substring(0, idx).trim();
                String v = s.substring(idx + 1).trim();
                URL url = null;
                try {
                    if ("configFile".equals(n)) {
                        url = new File(v).toURI().toURL();
                    } else if ("configURL".equals(n)) {
                        url = new URL(v);
                    } else {
                        List l = props.get(n);
                        if (l == null) {
                            l = new ArrayList();
                            props.put(n, l);
                        }
                        l.add(v);
                    }
                } catch (IOException ioex) {
                    ioex.printStackTrace();
                }
                if (url != null) {
                    try ( InputStream is = url.openStream();) {
                        Properties ps = new Properties();
                        ps.load(url.openStream());
                        for (Entry e : ps.entrySet()) {
                            List l = props.get(e.getKey().toString());
                            if (l == null) {
                                l = new ArrayList();
                                props.put(e.getKey().toString(), l);
                            }
                            l.add(e.getValue());
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        }
        for (String pn : props.keySet()) {
            if (pn.startsWith(base)) {
                String fn = pn.substring(base.length());
                if (fn.contains(".")) {
                    fn = fn.replace(".", "_");
                }
                if (fm.containsKey(fn)) {
                    try {
                        Field f = fm.get(fn);
                        List vl = props.get(pn);
                        for (Object vli : vl) {
                            Object v = refl.enrich(vli, f.getType());
                            if (f.getType().isArray()) {
                                if (f.get(config) == null) {
                                    f.set(config, v);
                                } else {
                                    Object vv = f.get(config);
                                    Object va=Array  .newInstance(f.getType().getComponentType(), Array.getLength(vv) + Array.getLength(v));
                                    int off = 0;
                                    for (Object oo : new Object[]{vv, v}) {
                                        for (int i = 0; i < Array.getLength(oo); i++) {
                                            Array.set(va, off++, Array.get(oo, i));
                                        }
                                    }
                                    f.set(config, va);
                                }
                            } else if (Collection.class.isAssignableFrom(f.getType())) {
                                if (f.get(config) == null) {
                                    f.set(config, v);
                                } else {
                                    Collection vv = (Collection) f.get(config);
                                    vv.addAll((Collection) v);
                                }
                            } else {
                                f.set(config, v);
                            }
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                } else {
                    if (config.other == null) {
                        config.other = new LinkedHashMap<>();
                    }
                    config.other.put(fn, props.get(pn));
                }
            }
        }
    }
}
