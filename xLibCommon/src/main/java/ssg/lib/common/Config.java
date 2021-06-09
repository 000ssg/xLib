/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
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
 * @author 000ssg
 */
public class Config {

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Description {

        /**
         * Describes the annotated item.
         *
         * @return item description
         */
        String value();

        /**
         * Describes expected value format.
         *
         * Use ";" to separate fields, use "," to separate values.
         *
         * @return
         */
        String pattern() default "";
    }

    static Refl refl = new Refl.ReflJSON();
    static JSON.Decoder jd = new JSON.Decoder(refl);
    static JSON.Encoder je = new JSON.Encoder(refl);
    private String base;
    private Map<String, Object> other;
    private boolean sysPropsLoaded = false;

    public Config(String base, String... args) {
        this.base = base != null ? base : getClass().isAnonymousClass() ? getClass().getSuperclass().getName() : getClass().getSimpleName();
        load(this, args);
    }

    public <T extends Config> T noSysProperties() {
        sysPropsLoaded = true;
        return (T) this;
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

    public static <T extends Config> T load(Config config, String... args) {
        String base = config.getBase().isEmpty() ? "" : config.getBase() + ".";

        Field[] fs = config.getClass().getFields();

        if (base.isEmpty() && args != null && args.length == 1 && args[0].startsWith("{")) try {
            Config ref = (Config) jd.readObject(args[0], config.getClass());
            for (Field f : fs) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.set(config, f.get(ref));
            }
            return (T) config;
        } catch (Throwable th) {
            th.printStackTrace();
            return (T) config;
        }

        Map<String, Field> fm = new HashMap<>();
        for (Field f : fs) {
            fm.put(f.getName(), f);
        }

        Map<String, List> props = new HashMap<>();
        if (!config.sysPropsLoaded) {
            Collection<URL> configSources = new ArrayList<>();
            for (Entry<String, String> e : System.getenv().entrySet()) {
                String pn = e.getKey();
                String v = e.getValue();
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
            for (Entry<Object, Object> e : System.getProperties().entrySet()) {
                String pn = (String) e.getKey();
                String v = (String) e.getValue();
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
                            Object v = null;
                            if (vli instanceof String
                                    && ((String) vli).length() > 1
                                    && !(String.class.equals(f.getType())
                                    && f.getType().isArray() && String.class.equals(f.getType().getComponentType())
                                    || (Collections.class.isAssignableFrom(f.getType())
                                    || f.getGenericType() instanceof ParameterizedType
                                    && String.class.equals(((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0])))) {
                                if (((String) vli).charAt(0) == '{') {
                                    // decode JSON object as generic type or as target type
                                    v = jd.readObject(
                                            (String) vli,
                                            f.getType().isArray()
                                            ? f.getType().getComponentType()
                                            : Collection.class.isAssignableFrom(f.getType())
                                            ? Map.class
                                            : f.getType()
                                    );
                                    v = refl.enrich(v, f.getType());
                                } else if (((String) vli).charAt(0) == '[') {
                                    v = jd.readObject((String) vli, List.class);
                                    v = refl.enrich(v, f.getType());
                                } else {
                                    v = refl.enrich(vli, f.getType());
                                }
                            } else {
                                v = refl.enrich(vli, f.getType());
                            }
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
        return (T) config;
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean withOthers) {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("base=" + base + (other != null && !other.isEmpty() ? ", other=" + other.size() : "") + ", sysPropsLoaded=" + sysPropsLoaded);
        Field[] fs = getClass().getFields();
        sb.append(", items=" + fs.length);
        for (boolean staticOnly : new boolean[]{true, false}) {
            for (Field f : fs) {
                if (staticOnly == Modifier.isStatic(f.getModifiers()))
            try {
                    sb.append("\n  ");
                    sb.append(f.getName());
                    sb.append(": ");
                    Description d = f.getAnnotation(Description.class
                    );
                    if (d
                            != null) {
                        if (d.value() != null) {
                            sb.append(d.value().replace("\n", "\n  "));
                        }
                        if (d.pattern() != null && !d.pattern().isEmpty()) {
                            sb.append("; pattern=" + d.pattern());
                        }
                    }

                    sb.append(
                            "\n    ");
                    sb.append(
                            "=");
                    Object o = (staticOnly ? f.get(null) : f.get(this));
                    if (o
                            == null) {
                        sb.append("<none>");
                    } else if (o instanceof Collection) {
                        sb.append(o.getClass().getName() + "[" + ((Collection) o).size() + "]");
                        for (Object oi : (Collection) o) {
                            sb.append("\n      " + ("" + oi).replace("\n", "\n      "));
                        }
                    } else if (o.getClass()
                            .isArray()) {
                        sb.append(o.getClass().getName() + "[" + Array.getLength(o) + "]");
                        for (int i = 0; i < Array.getLength(o); i++) {
                            Object oi = Array.get(o, i);
                            sb.append("\n      " + ("" + oi).replace("\n", "\n      "));
                        }
                    } else if (o instanceof Map) {
                        sb.append(o.getClass().getName() + "[" + ((Map) o).size() + "]");
                        for (Entry e : ((Map<?, ?>) o).entrySet()) {
                            sb.append("\n      " + e.getKey() + "=" + ("" + e.getValue()).replace("\n", "\n      "));
                        }
                    } else {
                        sb.append(("" + o).replace("\n", "\n    "));
                    }
                } catch (Throwable th) {
                    sb.append("\n  ERROR: " + ("" + th).replace("\n", "\n  "));
                }
            }
        }
        if (withOthers) {
            if (this.other != null && !this.other.isEmpty()) {
                sb.append("\n  other configs[" + other.size());
                for (Entry<String, Object> e : other.entrySet()) {
                    sb.append("\n    " + e.getKey() + "=" + ("" + e.getValue()).replace("\n", "\n    "));
                }
            }
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

}
