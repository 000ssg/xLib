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
package ssg.lib.wamp.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 *
 * @author 000ssg
 */
public class WAMPTools {

    public static final Map<String, Object> EMPTY_DICT = Collections.emptyMap();
    public static final List<Object> EMPTY_LIST = Collections.emptyList();

    public static String getStackTrace(Throwable th) {
        if (th != null) {
            try ( StringWriter sw = new StringWriter();) {
                th.printStackTrace(new PrintWriter(sw));
                return sw.toString();
            } catch (IOException ioex) {
            }
        }
        return (th != null) ? th.toString() : null;
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////// general collections creation
    ////////////////////////////////////////////////////////////////////////////
    public static <V> Set<V> createSet() {
        return createSet(true);
    }

    public static <V> Set<V> createSet(boolean keepOrder) {
        return createSet(keepOrder, (Consumer) null);
    }

    public static <V> Set<V> createSet(boolean keepOrder, V... items) {
        if (items == null || items.length == 0) {
            return createSet(keepOrder, (Consumer) null);
        } else {
            return createSet(keepOrder, (coll) -> {
                for (V v : items) {
                    coll.add(v);
                }
            });
        }
    }

    public static <V> Set<V> createSet(boolean keepOrder, Consumer<Set<V>> initializer) {
        Set<V> r = (keepOrder) ? new LinkedHashSet<>() : new HashSet<>();
        if (initializer != null) {
            initializer.accept(r);
        }
        return r;
    }

    public static <V> Set<V> createSynchronizedSet(boolean keepOrder, Consumer<Set<V>> initializer) {
        return Collections.synchronizedSet(createSet(keepOrder, initializer));
    }

    public static <V> List<V> createList(V... items) {
        if (items == null || items.length == 0) {
            return createList((Consumer) null);
        } else {
            return createList((list) -> {
                for (V v : items) {
                    list.add(v);
                }
            });
        }
    }

    public static <V> List<V> createList(Consumer<List<V>> initializer) {
        List<V> r = new ArrayList<>();
        if (initializer != null) {
            initializer.accept(r);
        }
        return r;
    }

    public static <V> List<V> createSynchronizedList() {
        return createSynchronizedList((Consumer) null);
    }

    public static <V> List<V> createSynchronizedList(V... items) {
        if (items == null || items.length == 0) {
            return createSynchronizedList((Consumer) null);
        } else {
            return createSynchronizedList((list) -> {
                for (V v : items) {
                    list.add(v);
                }
            });
        }
    }

    public static <V> List<V> createSynchronizedList(Consumer<List<V>> initializer) {
        List<V> r = Collections.synchronizedList(createList(initializer));
        return r;
    }

    public static <K, V> Map<K, V> createMap() {
        return createMap(true);
    }

    public static <K, V> Map<K, V> createMap(boolean keepOrder) {
        return createMap(keepOrder, null);
    }

    public static <K, V> Map<K, V> createMap(boolean keepOrder, Consumer<Map<K, V>> initializer) {
        Map<K, V> r = null;
        if (keepOrder) {
            r = new LinkedHashMap<>();
        } else {
            r = new HashMap<>();
        }
        if (initializer != null) {
            initializer.accept(r);
        }
        return r;
    }

    public static <K, V> Map<K, V> createSynchronizedMap() {
        return createSynchronizedMap(true);
    }

    public static <K, V> Map<K, V> createSynchronizedMap(boolean keepOrder) {
        return Collections.synchronizedMap(createMap(keepOrder));
    }

    public static <K, V> Map<K, V> createSynchronizedMap(boolean keepOrder, Consumer<Map<K, V>> initializer) {
        return Collections.synchronizedMap(createMap(keepOrder, initializer));
    }

    public static Map<String, Object> createDict(String key, Object value) {
        return createMap(true, (key != null) ? (map) -> {
            map.put(key, value);
        } : null);
    }

    public static Map<String, Object> createDict(String key, Object value, String key2, Object value2) {
        return createMap(true, (map) -> {
            if (key != null) {
                map.put(key, value);
            }
            if (key2 != null) {
                map.put(key2, value2);
            }
        });
    }

    public static Map<String, Object> createDict(Consumer<Map<String, Object>> initializer) {
        return createMap(true, initializer);
    }
}
