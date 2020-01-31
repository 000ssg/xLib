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
package ssg.lib.api.util;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author sesidoro
 */
public class Dumper {

    public static String dump(Object obj, Set processed) {
        if (processed == null) {
            processed = new HashSet();
        }
        StringBuilder sb = new StringBuilder();
        if (obj == null) {
            sb.append("null");
        } else if (obj.getClass().isPrimitive() || obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj);
        } else if (obj instanceof String
                || obj instanceof Class
                || obj instanceof File
                || obj instanceof URI
                || obj instanceof URL) {
            sb.append("\"");
            sb.append(obj);
            sb.append("\"");
        } else if (processed.contains(obj)) {
            if (obj instanceof Collection && ((Collection) obj).isEmpty()) {
                sb.append("[]");
            } else if (obj instanceof Map && ((Map) obj).isEmpty()) {
                sb.append("{}");
            } else {
                sb.append("#REF" + obj.getClass());
            }
        } else {
            processed.add(obj);
            if (obj instanceof Collection) {
                Collection e = (Collection) obj;
                if (!e.isEmpty()) {
                    boolean first = true;
                    sb.append("[");
                    for (Object eo : e) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(",");
                        }
                        sb.append("\n  " + dump(eo, processed).replace("\n", "\n  "));
                    }
                    sb.append("\n]");
                } else {
                    sb.append("[]");
                }
            } else if (obj.getClass().isArray()) {
                if (Array.getLength(obj) > 0) {
                    boolean first = true;
                    sb.append("[");
                    for (int i = 0; i < Array.getLength(obj); i++) {
                        Object eo = Array.get(obj, i);
                        if (first) {
                            first = false;
                        } else {
                            sb.append(",");
                        }
                        sb.append("\n  " + dump(eo, processed).replace("\n", "\n  "));
                    }
                    sb.append("\n]");
                } else {
                    sb.append("[]");
                }
            } else if (obj instanceof Map) {
                Map map = (Map) obj;
                sb.append("{");
                for (Object key : map.keySet()) {
                    try {
                        sb.append("\n  " + dump(key, processed).replace("\n", "\\n") + ": " + dump(map.get(key), processed).replace("\n", "\n  "));
                    } catch (Throwable th) {
                    }
                }
                sb.append("\n}");
            } else if (obj instanceof Enumeration) {
                processed.add(obj);
                Enumeration e = (Enumeration) obj;
                if (e.hasMoreElements()) {
                    boolean first = true;
                    sb.append("[");
                    while (e.hasMoreElements()) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(",");
                        }
                        sb.append("\n  " + dump(e.nextElement(), processed));
                    }
                    sb.append("\n]");
                } else {
                    sb.append("[]");
                }
            } else {
                sb.append("{");
                Set set = new HashSet();
                for (Field f : obj.getClass().getFields()) {
                    try {
                        sb.append("\n  \"" + f.getName() + "\": " + dump(f.get(obj), processed).replace("\n", "\n  "));
                        set.add(f.getName());
                    } catch (Throwable th) {
                    }
                }
                for (Method m : obj.getClass().getMethods()) {
                    if (!(m.getName().startsWith("get") || m.getName().startsWith("is"))) {
                        continue;
                    }
                    if (m.getParameterTypes().length > 0) {
                        continue;
                    }
                    String n = (m.getName().startsWith("get")) ? m.getName().substring(3) : m.getName().substring(2);
                    n = n.substring(0, 1).toLowerCase() + n.substring(1);
                    if (set.contains(n)) {
                        continue;
                    }
                    try {
                        sb.append("\n  \"" + n + "\": " + dump(m.invoke(obj), processed).replace("\n", "\n  "));
                        set.add(n);
                    } catch (Throwable th) {
                    }
                }
                sb.append("\n}");
            }
        }
        return sb.toString();
    }

    public static String dumpObj(Object obj) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Field f : obj.getClass().getFields()) {
                try {
                    sb.append(f.getName() + ": " + f.get(obj));
                    sb.append("\n");
                } catch (Throwable th) {
                }
            }
            for (Method m : obj.getClass().getMethods()) {
                try {
                    if (m.getParameterTypes().length == 0 && (m.getName().startsWith("is") || m.getName().startsWith("get"))) {
                        sb.append(m.getName() + "(): " + m.invoke(obj, null));
                        sb.append("\n");
                    }
                } catch (Throwable th) {
                }
            }
        } catch (Throwable th) {
        }
        return sb.toString();
    }

}
