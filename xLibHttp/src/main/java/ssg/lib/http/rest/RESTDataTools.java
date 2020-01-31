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
package ssg.lib.http.rest;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import ssg.lib.common.JSON;
import ssg.lib.common.Refl;
import ssg.lib.common.Refl.ReflJSON;

/**
 *
 * @author 000ssg
 */
public class RESTDataTools {

    static Set<Class> objectTypes = Collections.synchronizedSet(new HashSet<Class>());
    private static Refl refl = new ReflJSON();
    private static JSON.Encoder encoder = new JSON.Encoder(refl);
    private static JSON.Decoder decoder = new JSON.Decoder(refl);

    static {
        initObjectTypes();
    }

    /**
     * @return the mapper
     */
    public static JSON.Encoder getEncoder() {
        return encoder;
    }

    public static JSON.Decoder getDecoder() {
        return decoder;
    }

    private RESTDataTools() {
    }

    private static RESTDataTools getInstance(boolean createMissing) {
        return new RESTDataTools();
    }

    public static RESTDataTools getInstance() {
        return getInstance(true);
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////// utilities
    ////////////////////////////////////////////////////////////////////////////
    public static Set<Class> getObjectTypes() {
        return objectTypes;
    }

    static void initObjectTypes() {
        try {
            // fill in object types
            String daoPath = RESTDataTools.class.getName();
            daoPath = daoPath.substring(0, daoPath.lastIndexOf("." + RESTDataTools.class.getSimpleName()));
            daoPath = daoPath.substring(0, daoPath.lastIndexOf(".") + 1) + "dao";
            daoPath = daoPath.replace(".", "/");
            for (URL url : Collections.list(RESTDataTools.class.getClassLoader().getResources(daoPath))) {
                try {
                    File fld = new File(url.getFile());
                    if (fld.exists() && fld.isDirectory()) {
                        for (File f : fld.listFiles()) {
                            if (f.isFile() && f.getName().endsWith(".class")) {
                                String cn = url.getPath().substring(url.getPath().indexOf(daoPath)).replace("/", ".") + f.getName().substring(0, f.getName().length() - 6);
                                System.out.println("initWS: " + cn);
                                Class c = RESTDataTools.class.getClassLoader().loadClass(cn);
                                Annotation[] anns = c.getAnnotations();
                                //Entity e = (Entity) c.getAnnotation(Entity.class);
                                if (anns != null) {
                                    for (Annotation ann : anns) {
                                        if (ann != null && ann.getClass().getName().equals("Entity")) {
                                            objectTypes.add(c);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (ClassNotFoundException cnfex) {
                    cnfex.printStackTrace();
                }
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
    }

    /**
     * converts object into map of named values based on public getters.
     *
     * @param obj
     * @param objectTypes - list of classes to consider as reference (skipped
     * from serialization)
     * @return
     */
    public static Map<String, Object> entityToMap(Object obj, Collection<Class> objectTypes) {
        if (objectTypes == null) {
            objectTypes = RESTDataTools.objectTypes;
        }

        if (obj == null) {
            return null;
        } else if (obj.getClass().isPrimitive()) {
            return null;
        }

        Method[] ms = obj.getClass().getMethods();
        Map<String, Object> map = new LinkedHashMap<String, Object>();

        for (Method m : ms) {
            if (m.getName().length() > 3
                    && m.getName().startsWith("get")
                    && (m.getParameterTypes() == null || m.getParameterTypes().length == 0)
                    && !void.class.equals(m.getReturnType())) {
                String key = m.getName().substring(3, 4).toLowerCase() + ((m.getName().length() > 4) ? m.getName().substring(4) : "");
                Object value = null;

                try {
                    value = m.invoke(obj);
                } catch (Throwable th) {
                }
                Class vc = (value != null) ? value.getClass() : void.class;

                if (vc.isArray()) {
                    vc = vc.getComponentType();
                }
                if (value instanceof List) {
                    // entity may have only scalar attributes, lists are for references
                    continue;
                }
                if (value instanceof Class || value != null && objectTypes.contains(value.getClass())) {
                    // value of class or registered type is not property - ignored
                    continue;
                }
                map.put(key, value);
            }
        }
        return map;
    }

    static DateFormat[] dfs = new DateFormat[]{
        // JSON-friendly formats
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz"),
        new SimpleDateFormat("yyyy-MM-dd"),
        // additional formats
        new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"),
        //new SimpleDateFormat("yyyy-MM-dd hh:mm"),
        new SimpleDateFormat("dd-MM-yyyy hh:mm:ss"),
        //new SimpleDateFormat("dd-MM-yyyy hh:mm"),
        new SimpleDateFormat("dd-MM-yyyy")
    };

    public static Object convertToType(Class type, Object value, boolean ignorable) throws IOException {
        if (type == null || value == null) {
            return null;
        }
        Object result = null;
        if (type.isAssignableFrom(value.getClass())) {
            result = value;
        } else if (String.class.equals(type)) {
            result = value.toString();
        } else if (type.isEnum()) {
            try {
                Method m = type.getMethod("valueOf", String.class);
                result = m.invoke(null, value.toString());
            } catch (Throwable th) {
                throw new IOException("Enumeration value error", th);
            }
        } else if (String.class.equals(value.getClass()) && ((String) value).isEmpty() && !type.isPrimitive()) {
            result = null;
        } else if (Date.class.equals(type)) {
            try {
                // try to interpret String as long
                if (value instanceof String) {
                    value = Long.parseLong(value.toString());
                }
            } catch (Throwable th) {
            }
            if (value instanceof String) {
                for (DateFormat df : dfs) {
                    try {
                        Date d = df.parse(value.toString());
                        if (df.format(d).equals(value)) {
                            result = d;
                            break;
                        }
                    } catch (Throwable th) {
                    }
                }
            } else if (value instanceof Number) {
                result = new Date(((Number) value).longValue());
            }
        } else if (RESTDataTools.getObjectTypes().contains(type)) {
            String s = null;
            if (value instanceof String) {
                s = (String) value;
            } else {
                if (value instanceof Map) {
                    // get type properties
                    Set<String> tprops = cachedProperties.get(type);
                    if (tprops == null) {
                        try {
                            Map<String, Object> tm = RESTDataTools.entityToMap(type.newInstance(), null);
                            tprops = tm.keySet();
                            cachedProperties.put(type, tprops);
                        } catch (Throwable th) {
                            tprops = new HashSet<String>();
                            cachedProperties.put(type, tprops);
                        }
                    }
                    // fix map keys to start with lowercase letter
                    Map m = (Map) value;
                    Map mm = new LinkedHashMap<String, Object>();
                    for (Object k : m.keySet()) {
                        String key = ((String) k).substring(0, 1).toLowerCase() + ((String) k).substring(1);
                        if (tprops.contains(k) || tprops.contains(key)) {
                            mm.put(key, m.get(k));
                        }
                    }
                    value = mm;
                }
                s = getEncoder().writeObject(value); // getMapper().writeValueAsString(value);
            }
            result = getDecoder().readObject(s, type); // getMapper().readValue(s, type);
        } else if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            if (value instanceof String) {
                result = Boolean.parseBoolean((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).longValue() != 0;
            } else {
                result = false;
            }
        } else if (Byte.class.equals(type) || byte.class.equals(type)) {
            if (value instanceof String) {
                result = Byte.parseByte((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).byteValue();
            }
        } else if (Short.class.equals(type) || short.class.equals(type)) {
            if (value instanceof String) {
                result = Short.parseShort((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).shortValue();
            }
        } else if (Integer.class.equals(type) || int.class.equals(type)) {
            if (value instanceof String) {
                result = Integer.parseInt((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).intValue();
            }
        } else if (Long.class.equals(type) || long.class.equals(type)) {
            if (value instanceof String) {
                result = Long.parseLong((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).longValue();
            }
        } else if (Float.class.equals(type) || float.class.equals(type)) {
            if (value instanceof String) {
                result = Float.parseFloat((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).floatValue();
            }
        } else if (Double.class.equals(type) || double.class.equals(type)) {
            if (value instanceof String) {
                result = Double.parseDouble((String) value);
            } else if (value instanceof Number) {
                result = ((Number) value).doubleValue();
            }
        } else if (type.isAssignableFrom(List.class)) {
            if (value instanceof List) {
                result = value;
            } else if (value.getClass().isArray()) {
                result = Arrays.asList((Object[]) value);
            } else {
                result = Collections.singletonList(value);
            }
        } else if (type.isArray()) {
            if (value.getClass().isArray()) {
                result = value;
            } else if (value instanceof Collection) {
                //result = ((Collection) value).toArray((Object[]) Array.newInstance(type.getComponentType(), ((Collection) value).size()));
                Collection values = (Collection) value;
                Object[] ar = (Object[]) Array.newInstance(type.getComponentType(), values.size());
                int idx = 0;
                for (Object o : values) {
                    ar[idx++] = convertToType(type.getComponentType(), o, ignorable);
                }
                result = ar;
            } else if (value.getClass().isArray()) {
                Object[] values = (Object[]) value;
                Object[] ar = (Object[]) Array.newInstance(type.getComponentType(), values.length);
                for (int i = 0; i < values.length; i++) {
                    ar[i] = convertToType(type.getComponentType(), values[i], ignorable);
                }
                result = ar;
//            } else if (value instanceof ArrayNode) {
//                ArrayNode values = (ArrayNode) value;
//                Object[] ar = (Object[]) Array.newInstance(type.getComponentType(), values.size());
//                for (int i = 0; i < values.size(); i++) {
//                    ar[i] = convertToType(type.getComponentType(), values.get(i), ignorable);
//                }
//                result = ar;
            } else {
                Object ar = null;
                if (type.getComponentType().isPrimitive()) {
                    Class ac = type.getComponentType();
                    if (byte.class == ac) {
                        ar = new byte[1];
                    } else if (short.class == ac) {
                        ar = new short[1];
                    } else if (int.class == ac) {
                        ar = new int[1];
                    } else if (long.class == ac) {
                        ar = new long[1];
                    } else if (float.class == ac) {
                        ar = new float[1];
                    } else if (double.class == ac) {
                        ar = new double[1];
                    } else if (char.class == ac) {
                        ar = new char[1];
                    }
                } else {
                    ar = (Object[]) Array.newInstance(type.getComponentType(), 1);
                }
                Array.set(ar, 0, convertToType(type.getComponentType(), value, ignorable));
                result = ar;
            }
        } else {
            // value dropped
            String msg = "Failed to adjust value type=" + value.getClass() + " to type " + type + ". Value is '" + value + "'";
            if (ignorable) {
                System.err.println("WARNING: " + msg);
            } else {
                throw new IOException(msg);
            }
        }
        return result;
    }
    private static Map<Class, Set<String>> cachedProperties = Collections.synchronizedMap(new HashMap<Class, Set<String>>());

}
