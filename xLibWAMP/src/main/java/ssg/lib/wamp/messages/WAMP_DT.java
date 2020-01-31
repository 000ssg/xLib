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
package ssg.lib.wamp.messages;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public enum WAMP_DT {
    uri, id, integer, string, bool, dict, list;

    public static final long MAX_WAMP_INT = 0xFFFFFFF; // 56 bits int only...

    String toNamed(String name) {
        return name + "|" + this;
    }

    public boolean validate(Object value) {
        return validate(this, value);
    }

    public static WAMP_DT fromNamed(String nt) {
        if (nt != null) {
            String[] ss = nt.split("\\|");
            if (ss.length > 1) {
                return valueOf(ss[1]);
            }
        }
        return null;
    }

    public static String nameOf(String nt) {
        if (nt != null) {
            String[] ss = nt.split("\\|");
            return ss[0];
        }
        return nt;
    }

    public static boolean validate(WAMP_DT type, Object value) {
        if (type == null || value == null) {
            return false;
        }
        switch (type) {
            case uri:
                if (value instanceof URL || value instanceof URI) {
                    return true;
                }
                if (value instanceof String) {
                    String s = (String) value;
                    String[] ss = s.split("\\.");
                    for (String si : ss) {
                        for (char ch : " \t\n\r\f#".toCharArray()) {
                            if (si.indexOf(ch) != -1) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
                return false;
            case integer:
                if (value instanceof Number) {
                    return true;
                }
                return false;
            case id:
                if (value instanceof Number) {
                    if (((Number) value).longValue() > 0 && ((Number) value).longValue() <= MAX_WAMP_INT) {
                        return true;
                    }
                }
                return false;
            case string:
                if (value instanceof String) {
                    return true;
                }
                return false;
            case bool:
                if (value instanceof Boolean) {
                    return true;
                }
                return false;
            case dict:
                if (value instanceof Map) {
                    for (Object key : ((Map) value).keySet()) {
                        if (!(key instanceof String)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            case list:
                if (value instanceof Collection) {
                    return true;
                } else if (value.getClass().isArray()) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }
}
