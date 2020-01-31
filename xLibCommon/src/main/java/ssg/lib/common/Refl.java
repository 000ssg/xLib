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
package ssg.lib.common;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.crypto.Data;

/**
 *
 * @author sesidoro
 */
public interface Refl {

    void clear();

    void clearRefs();

    Collection<String> names(Object obj);

    <T> T value(Object obj, String name);

    void set(Object obj, String name, Object value);

    <T> T simplify(Object obj) throws IOException;

    <T> T enrich(Object obj, Class type, Type... xtypes) throws IOException;

    <T> T enrichArray(Object obj, Class type, Type... xtypes) throws IOException;

    <T> T simplifyArray(Object obj) throws IOException;

    String simplifyString(String s);

    String enrichString(String s);

    Date parseDate(String s) throws IOException;

    /**
     * Returns all directly specified interfaces
     *
     * @param obj
     * @return
     */
    Collection<Class> interfaces(Object obj);

    /**
     * Returns all directly specified annotations
     *
     * @param obj
     * @return
     */
    Collection<Annotation> annotations(Object obj);

    /**
     * Returns java proxy for specified item limited to specified interfaces or,
     * if none, to those evaluated with interfaces(obj) method.
     *
     * NOTE: the methods invocation is provided via getInvocationHandler method.
     *
     * @param <T>
     * @param obj
     * @param interfaces
     * @return
     */
    <T> T proxy(Object obj, Class... interfaces);

    /**
     * Returns invocation handler for specified object/interfaces needed to
     * build proxy class.
     *
     * @param obj
     * @param interfaces
     * @return
     */
    java.lang.reflect.InvocationHandler getInvocationHandler(Object obj, Class... interfaces);

    public static class ReflImpl implements Refl {

        Map<Class, Map<String, Object[]>> cache = new LinkedHashMap<>();
        ThreadLocal<Map<Integer, Object>> processed = new ThreadLocal<Map<Integer, Object>>() {
            @Override
            protected Map<Integer, Object> initialValue() {
                return new HashMap<Integer, Object>() {
                    int base = 0;

                    @Override
                    public Object put(Integer key, Object value) {
                        if (value == null) {
                            value = base++;
                        }
                        return super.put(key, value);
                    }

                };
            }
        };

        @Override
        public void clear() {
            cache.clear();
        }

        @Override
        public void clearRefs() {
            Map<Integer, Object> processedRef = processed.get();
            processedRef.clear();
        }

        @Override
        public Collection<String> names(Object obj) {
            if (obj == null) {
                return Collections.emptyList();
            }
            Class cl = obj.getClass();
            Map<String, Object[]> ns = cache.get(cl);
            if (ns == null) {
                init(cl);
                ns = cache.get(cl);
            }
            return ns.keySet();
        }

        @Override
        public <T> T value(Object obj, String name) {
            if (obj == null) {
                return null;
            }
            Class cl = obj.getClass();
            Map<String, Object[]> ns = cache.get(cl);
            if (ns == null) {
                init(cl);
                ns = cache.get(cl);
            }
            Object[] acc = ns.get(name);
            if (acc[0] instanceof Field) {
                try {
                    return (T) ((Field) acc[0]).get(obj);
                } catch (Throwable th) {
                }
            } else if (acc[0] instanceof Method) {
                try {
                    return (T) ((Method) acc[0]).invoke(obj);
                } catch (Throwable th) {
                }
            }
            return null;
        }

        @Override
        public void set(Object obj, String name, Object value) {
            if (obj == null) {
                return;
            }
            Class cl = obj.getClass();
            Map<String, Object[]> ns = cache.get(cl);
            if (ns == null) {
                init(cl);
                ns = cache.get(cl);
            }
            Object[] acc = ns.get(name);
            if (acc[0] instanceof Field) {
                try {
                    Field f = ((Field) acc[0]);
                    f.set(obj, (Object) enrich(value, f.getType(), f.getGenericType()));
                } catch (Throwable th) {
                }
            } else if (acc[1] instanceof Method) {
                try {
                    Method m = ((Method) acc[1]);
                    m.invoke(obj, (Object) enrich(
                            value,
                            m.getParameterTypes()[0],
                            (m.getGenericParameterTypes() != null && m.getGenericParameterTypes().length == 1) ? m.getGenericParameterTypes()[0] : null));
                } catch (Throwable th) {
                    int a = 0;
                }
            }
        }

        void init(Class cl) {
            if (cl == null || Class.class.isAssignableFrom(cl)) {
                return;
            }
            Map<String, Object[]> accs = new HashMap<>();
            cache.put(cl, accs);
            try {
                for (Field f : cl.getFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
                        continue;
                    }
                    accs.put(f.getName(), new Object[]{f, f});
                }
                // gets..
                for (Method f : cl.getMethods()) {
                    if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) {
                        continue;
                    }
                    if (f.getParameterCount() > 0) {
                        continue;
                    }
                    String n = f.getName();
                    if (n.startsWith("is") || n.startsWith("get")) {
                        if (f.getName().equals("getClass")) {
                            continue;
                        }
                        if (n.startsWith("is")) {
                            n = n.substring(2);
                        } else {
                            n = n.substring(3);
                        }
                        n = n.substring(0, 1).toLowerCase() + n.substring(1);
                        if (!accs.containsKey(n)) {
                            accs.put(n, new Object[]{f, null});
                        }
                    }
                }
                // sets..
                for (Method f : cl.getMethods()) {
                    if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) {
                        continue;
                    }
                    if (f.getParameterCount() != 1 || f.getReturnType() == null) {
                        continue;
                    }
                    String n = f.getName();
                    if (n.startsWith("set")) {
                        n = n.substring(3);
                        n = n.substring(0, 1).toLowerCase() + n.substring(1);
                        Object[] acc = accs.get(n);
                        if (acc == null) {
                            accs.put(n, new Object[]{null, f});
                        } else {
                            if (acc[1] == null) {
                                acc[1] = f;
                            }
                        }
                    }
                }
            } catch (Throwable th) {
            }
        }

        @Override
        public <T> T simplify(Object obj) throws IOException {
            if (obj == null) {
                return null;
            }
            Map<Integer, Object> processedRef = processed.get();
            if (processedRef != null && processedRef.containsKey(System.identityHashCode(obj))) {
                return (T) ("#REF:" + processedRef.get(System.identityHashCode(obj)));
            }
            if (obj instanceof Collection) {
                List lst = new ArrayList();
                if (processedRef != null) {
                    processedRef.put(System.identityHashCode(obj), null);
                }
                for (Object item : (Collection) obj) {
                    lst.add(simplify(item));
                }
                return (T) lst;
            } else if (obj.getClass().isArray()) {
                if (processedRef != null) {
                    processedRef.put(System.identityHashCode(obj), null);
                }
                return simplifyArray(obj);
            } else if (obj instanceof Enumeration) {
                if (processedRef != null) {
                    processedRef.put(System.identityHashCode(obj), null);
                }
                List lst = new ArrayList();
                for (Object item : Collections.list((Enumeration) obj)) {
                    lst.add(simplify(item));
                }
                return (T) lst;
            } else if (obj instanceof Map) {
                if (processedRef != null) {
                    processedRef.put(System.identityHashCode(obj), null);
                }
                return (T) obj;
            } else if (obj instanceof String || obj instanceof File || obj instanceof URL || obj instanceof URI) {
                return (T) simplifyString("" + obj);
            } else if (obj.getClass().isPrimitive() || obj instanceof Number || obj instanceof String || obj instanceof Boolean || obj instanceof File || obj instanceof URL || obj instanceof URI) {
                return (T) ("" + obj);
            } else {
                if (processedRef != null) {
                    processedRef.put(System.identityHashCode(obj), null);
                }
                Map m = new LinkedHashMap();
                for (String s : names(obj)) {
                    m.put(s, simplify(value(obj, s)));
                }
                return (T) m;
            }
        }

        @Override
        public <T> T enrich(Object obj, Class type, Type... xtypes) throws IOException {
            if (type != null) {
                if (obj == null) {
                    return null;
                } else if (obj.getClass() == type || type.isAssignableFrom(obj.getClass())) {
                    if (!Collection.class.isAssignableFrom(type)) {
                        return (T) obj;
                    }
                }

                ParameterizedType ptype = (xtypes != null && xtypes.length > 0 && xtypes[0] instanceof ParameterizedType) ? (ParameterizedType) xtypes[0] : null;

                try {
                    if (type.isArray()) {
                        return enrichArray(obj, type, xtypes);
                    } else if (Collection.class.isAssignableFrom(type)) {
                        int sz = ((obj instanceof Collection)) ? ((Collection) obj).size() : (obj.getClass().isArray()) ? Array.getLength(obj) : 1;
                        Collection o = createInstance(type, xtypes);
                        Class itype = (ptype != null
                                && ptype.getActualTypeArguments() != null
                                && ptype.getActualTypeArguments().length == 1
                                && ptype.getActualTypeArguments()[0] instanceof Class)
                                ? (Class) ptype.getActualTypeArguments()[0]
                                : null;
                        if (obj instanceof Collection) {
                            for (Object oi : (Collection) obj) {
                                o.add((itype != null) ? enrich(oi, itype) : oi);
                            }
                        } else if (obj.getClass().isArray()) {
                            for (int i = 0; i < sz; i++) {
                                o.add((itype != null) ? enrich(Array.get(obj, i), itype) : Array.get(obj, i));
                            }
                        } else {
                            o.add((itype != null) ? enrich(obj, itype) : obj);
                        }
                        return (T) o;
                        //} else if (type.isPrimitive()) {

                    } else if (String.class == type) {
                        return (T) enrichString(obj.toString());
                    } else if (Number.class.isAssignableFrom(type)
                            || byte.class == type
                            || short.class == type
                            || int.class == type
                            || long.class == type
                            || float.class == type
                            || double.class == type) {
                        Number n = (obj instanceof Number) ? (Number) obj : null;
                        String s = (n == null) ? obj.toString() : null;
                        int rad = 10;
                        if (s != null && (s.startsWith("0x") || s.startsWith("0X"))) {
                            s = s.substring(2);
                            rad = 16;
                        }
                        if (Byte.class == type || byte.class == type) {
                            Byte b = ((n != null) ? n.byteValue() : Byte.parseByte(s, rad));
                            return (T) b;
                        } else if (Short.class == type || short.class == type) {
                            Short b = ((n != null) ? n.shortValue() : Short.parseShort(s, rad));
                            return (T) b;
                        } else if (Integer.class == type || int.class == type) {
                            Integer b = ((n != null) ? n.intValue() : Integer.parseInt(s, rad));
                            return (T) b;
                        } else if (Long.class == type || long.class == type) {
                            Long b = ((n != null) ? n.longValue() : Long.parseLong(s, rad));
                            return (T) b;
                        } else if (Float.class == type || float.class == type) {
                            Float b = ((n != null) ? n.floatValue() : Float.parseFloat(s));
                            return (T) b;
                        } else if (Double.class == type || double.class == type) {
                            Double b = ((n != null) ? n.doubleValue() : Double.parseDouble(s));
                            return (T) b;
//                        } else if (BigInteger.class == type) {
//                            Byte b = ((n != null) ? n.byteValue() : Byte.parseByte(s));
//                            return (T) b;
                        } else if (BigInteger.class == type) {
                            BigInteger b = null;
                            if (n != null) {
                                b = BigInteger.valueOf(n.longValue());
                            } else if (obj instanceof byte[]) {
                                b = new BigInteger((byte[]) obj);
                            } else {
                                b = BigInteger.valueOf(Long.parseLong(s, rad));
                            }
                            return (T) b;
                        } else if (BigDecimal.class == type) {
                            BigDecimal b = null;
                            if (n != null) {
                                if (n instanceof Float || n instanceof Double) {
                                    b = BigDecimal.valueOf(n.doubleValue());
                                } else {
                                    b = BigDecimal.valueOf(n.longValue());
                                }
                            } else if (s != null) {
                                if (s.contains(".") || (s.contains("e") || s.contains("E") && rad != 16)) {
                                    b = BigDecimal.valueOf(Double.parseDouble(s));
                                } else {
                                    b = BigDecimal.valueOf(Long.parseLong(s, rad));
                                }
                            }
                            return (T) b;
                        } else if (File.class == type || URI.class == type || URL.class == type) {
                            Constructor c = type.getConstructor(String.class);
                            return (T) c.newInstance(enrichString(obj.toString()));
                        } else {
                            // exception?
                            return (T) obj;
                        }
                    } else if (Boolean.class == type || boolean.class == type) {
                        if (obj instanceof Boolean) {
                            return (T) obj;
                        } else {
                            return (T) Boolean.valueOf(obj.toString());
                        }
                    } else if (Data.class == type) {
                        if (type.isAssignableFrom(obj.getClass())) {
                            return (T) obj;
                        }
                        if (obj instanceof String) {
                            // parse as date
                            return (T) parseDate((String) obj);
                        } else if (obj instanceof Number) {
                            return (T) new Date(((Number) obj).longValue());
                        }
                    } else if (obj instanceof Map) {
                        Map m = (Map) obj;
                        Object o = type.newInstance();
                        Collection<String> props = this.names(o);
                        for (String pn : props) {
                            set(o, pn, m.get(pn));
                        }
                        return (T) o;
                    }
                } catch (InstantiationException ex) {
                    throw new IOException(ex);
                } catch (IllegalAccessException ex) {
                    throw new IOException(ex);
                } catch (Throwable th) {
                    throw new IOException(th);
                }
            }
            return (T) obj;
        }

        @Override
        public <T> T enrichArray(Object obj, Class type, Type... xtypes) throws IOException {
            int sz = ((obj instanceof Collection)) ? ((Collection) obj).size() : (obj.getClass().isArray()) ? Array.getLength(obj) : 1;
            Object o = Array.newInstance(type.getComponentType(), sz);
            if (obj instanceof Collection) {
                int idx = 0;
                for (Object oi : (Collection) obj) {
                    Array.set(o, idx++, enrich(oi, type.getComponentType(), type.getGenericSuperclass()));
                }
            } else if (obj.getClass().isArray()) {
                for (int i = 0; i < sz; i++) {
                    Array.set(o, i, enrich(Array.get(obj, i), type.getComponentType(), type.getGenericSuperclass()));
                }
            } else {
                Array.set(o, 0, obj);
            }
            return (T) o;
        }

        @Override
        public <T> T simplifyArray(Object obj) throws IOException {
            List lst = new ArrayList();
            for (Object item : Arrays.asList(obj)) {
                lst.add(simplify(item));
            }
            return (T) lst;
        }

        @Override
        public String simplifyString(String s) {
            return s;
        }

        @Override
        public String enrichString(String s) {
            return s;
        }

        @Override
        public Date parseDate(String s) throws IOException {
            return null;
        }

        public <T> T createInstance(Class type, Type... xtype) throws InstantiationException, IllegalAccessException {
            if (type.isInterface()) {
                if (List.class == type) {
                    return (T) new ArrayList();
                } else if (Map.class == type) {
                    return (T) new LinkedHashMap();
                } else if (Set.class == type) {
                    return (T) new LinkedHashSet();
                }
            }
            return (T) type.newInstance();
        }

        public <T> T createSpecificInstance(Class type, Type... xtype) throws InstantiationException, IllegalAccessException {
            return (T) type.newInstance();
        }

        /**
         * Returns bean-like interface info for a class: key - property name,
         * Object[][]=[getType, setType][getter, setter]. if no set Type -> read
         * only property.
         *
         * @param type
         * @return
         */
        public Map<String, Object[][]> getBeanInfo(Class type, boolean blockFields) {
            Map<String, Object[][]> r = new LinkedHashMap<>();

            Map<String, Object[]> ns = cache.get(type);
            if (ns == null) {
                init(type);
                ns = cache.get(type);
            }

            if (ns != null && !ns.isEmpty()) {
                for (Entry<String, Object[]> e : ns.entrySet()) {
                    Object[][] cls = new Object[2][2];
                    if (e.getValue()[0] instanceof Field) {
                        if (!blockFields) {
                            cls[0][0] = ((Field) e.getValue()[0]).getType();
                            cls[0][1] = e.getValue()[0];
                        }
                    } else if (e.getValue()[0] instanceof Method) {
                        cls[0][0] = ((Method) e.getValue()[0]).getReturnType();
                        cls[0][1] = e.getValue()[0];
                    }
                    if (e.getValue()[1] instanceof Field) {
                        if (!blockFields) {
                            cls[1][0] = ((Field) e.getValue()[1]).getType();
                            cls[1][0] = e.getValue()[1];
                        }
                    } else if (e.getValue()[1] instanceof Method) {
                        cls[1][0] = ((Method) e.getValue()[1]).getParameterTypes()[0];
                        cls[1][1] = e.getValue()[1];
                    }
                    r.put(e.getKey(), cls);
                }
            }
            return r;
        }

        @Override
        public Collection<Class> interfaces(Object obj) {
            List<Class> r = new ArrayList();
            Class[] cls = (obj instanceof Class) ? new Class[]{(Class) obj} : (obj != null) ? new Class[]{obj.getClass()} : null;

            if (cls != null) {
                for (Class cl : cls) {
                    if (cl == null) {
                        continue;
                    }
                    if (cl.isInterface()) {
                        if (!r.contains(cl)) {
                            r.add(cl);
                        }
                    } else if (cl.getInterfaces() != null) {
                        for (Class cli : cl.getInterfaces()) {
                            if (!r.contains(cli)) {
                                r.add(cli);
                            }
                        }
                    }
                }
            }

            return r;
        }

        @Override
        public Collection<Annotation> annotations(Object obj) {
            List<Annotation> r = new ArrayList<>();
            Class[] cls = (obj instanceof Class) ? new Class[]{(Class) obj} : (obj != null) ? new Class[]{obj.getClass()} : null;

            if (cls != null) {
                for (Class cl : cls) {
                    Annotation[] cas = cl.getAnnotations();
                    if (cas != null) {
                        for (Annotation ca : cas) {
                            if (!r.contains(ca)) {
                                r.add(ca);
                            }
                        }
                    }
                }
            }

            return r;
        }

        public <T> T proxy(Object obj, Class... interfaces) {
            if (interfaces == null || interfaces.length == 0) {
                Collection<Class> ifs = interfaces(obj);
                interfaces = ifs.toArray(new Class[ifs.size()]);
            }
            T t = (T) java.lang.reflect.Proxy.newProxyInstance(obj.getClass().getClassLoader(),
                    interfaces,
                    getInvocationHandler(obj, interfaces));
            return t;
        }

        public java.lang.reflect.InvocationHandler getInvocationHandler(Object obj, Class... interfaces) {
            return new ReflProxy(obj, this, interfaces);
        }

        public static Class getProxyObjectType(java.lang.reflect.Proxy proxy) {
            if (proxy == null) {
                return null;
            }
            try {
                Field[] ff = proxy.getClass().getSuperclass().getDeclaredFields();
                for (Field f : ff) {
                    if ("h".equals(f.getName()) && java.lang.reflect.InvocationHandler.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object o = f.get(proxy);
                        if (o instanceof Refl.ReflProxy) {
                            return ((Refl.ReflProxy) o).getType();
                        }
                    }
                }
            } catch (Throwable th) {
                //th.printStackTrace();
            }
            return null;
        }

        public static List<Method> getProxyMethods(java.lang.reflect.Proxy proxy) {
            List<Method> pmss = new ArrayList<>();
            Field[] ff0 = proxy.getClass().getDeclaredFields();
            for (Field f : ff0) {
                f.setAccessible(true);
                //System.out.println(" ff0:" + f.getName() + ":" + f.getType() + ":" + f.get(proxy));
                if (Method.class.isAssignableFrom(f.getType())) {
                    try {
                        Method m = (Method) f.get(proxy);
                        if ("equals".equals(m.getName())) {
                        } else if ("toString".equals(m.getName())) {
                        } else if ("hashCode".equals(m.getName())) {
                        } else {
                            pmss.add(m);
                        }
                    } catch (Throwable th) {
                    }
                }
            }
            return pmss;
        }

    }

    /**
     * Default implementation of InvocationHandler used for proxy.
     */
    public static class ReflProxy implements java.lang.reflect.InvocationHandler {

        Object obj;
        Refl refl;
        Class[] interfaces;

        public ReflProxy(Object obj, Refl refl, Class... interfaces) {
            this.obj = obj;
            this.refl = refl;
            this.interfaces = interfaces;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = null;
            try {
                before(method, args);
                result = execute(method, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } catch (Throwable th) {
                throw error(th);
            } finally {
                result = after(method, result, args);
            }
            return result;
        }

        /**
         * Returns type (class) of proxied item.
         *
         * @return
         */
        public Class getType() {
            return (obj != null) ? obj.getClass() : null;
        }

        public void before(Method m, Object... args) throws Throwable {
        }

        public Object execute(Method m, Object... args) throws Throwable {
            return m.invoke(obj, args);
        }

        public Object after(Method m, Object result, Object... args) throws Throwable {
            return result;
        }

        public Throwable error(Throwable th) throws Throwable {
            throw new RuntimeException("unexpected invocation exception: "
                    + th.getMessage());
        }
    }

    public static class ReflJSON extends ReflImpl {

        String dateTimeFormatS = "dd-MM-yyyyTHH:mm:ss";
        Base64.Encoder encoder = Base64.getEncoder();
        Base64.Decoder decoder = Base64.getDecoder();
        ThreadLocal<DateFormat> dtf = new ThreadLocal<DateFormat>() {
            @Override
            protected DateFormat initialValue() {
                return new SimpleDateFormat(dateTimeFormatS);
            }
        };

        @Override
        public <T> T simplifyArray(Object obj) throws IOException {
            if (obj instanceof byte[]) {
                return (T) new String(encoder.encode((byte[]) obj));
            } else {
                return super.simplifyArray(obj);
            }
        }

        @Override
        public <T> T enrichArray(Object obj, Class type, Type... xtypes) throws IOException {
            if (type == byte[].class && obj instanceof String) {
                return (T) decoder.decode((String) obj);
            } else {
                return super.enrichArray(obj, type, xtypes);
            }
        }

        @Override
        public Date parseDate(String s) throws IOException {
            try {
                return dtf.get().parse(s);
            } catch (Throwable th) {
                throw new IOException("Failed to parse JSON date as '" + dateTimeFormatS + "', got '" + s + "'", th);
            }
        }

    }
}
