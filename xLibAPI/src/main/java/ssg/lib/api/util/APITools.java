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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.util.APISearchable.APIMatcher;
import ssg.lib.api.util.APISearchable.APIMatcher.API_MATCH;

/**
 *
 * @author sesidoro
 */
public class APITools {

    public static boolean DUMP_SEARCH_ITEMS = false;

    public static boolean hasType(Class type, Class... types) {
        if (type == null || types == null || types.length == 0) {
            return false;
        }
        for (Class t : types) {
            if (t != null) {
                if (t.isAssignableFrom(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static <T extends APIItem> Collection<T> find(
            APIMatcher matcher,
            Class type,
            Collection<T> result,
            Map... data
    ) {
        if (result == null) {
            result = new HashSet<>();
        }

        if (data != null) {
            for (Map<String, Object> map : data) {
                if (map == null) {
                    continue;
                }
                for (Entry<String, Object> e : map.entrySet()) {
                    Object obj = e.getValue();
                    if (!(obj instanceof APIItem || obj instanceof APIItem[])) {
                        continue;
                    }
                    APIItem[] items = (obj instanceof APIItem) ? new APIItem[]{(APIItem) obj} : (APIItem[]) obj;
                    for (APIItem item : items) {
                        if (matcher == null) {
                            if (type == null || type.isAssignableFrom(item.getClass())) {
                                result.add((T) item);
                            }
                        } else {
                            API_MATCH m = matcher.matches(item);
                            switch (m) {
                                case exact:
                                    if (type == null || type.isAssignableFrom(item.getClass())) {
                                        result.add((T) item);
                                    }
                                    break;
                                case partial:
                                    if (item instanceof APISearchable) {
                                        result = ((APISearchable) item).find(matcher, type, result);
                                    }
                                    break;
                                case none:
                                    break;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public static float testParameters(APIProcedure proc, Object params) {
        if (proc == null) {
            return 0;
        }
        if (params instanceof Map) {
            return proc.testParameters((Map) params);
        } else if (params instanceof List) {
            return proc.testParameters((List) params);
        } else if (params != null && params.getClass().isArray()) {
            if (params.getClass().getComponentType().isPrimitive()) {
                return proc.testParameters(new Object[]{params});
            } else {
                return proc.testParameters((Object[]) params);
            }
        } else if (params != null) {
            return proc.testParameters(new Object[]{params});
        } else {
            return proc.testParameters((List) null);
        }
    }
}
