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
package ssg.lib.api;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author 000ssg
 */
public class APIAccess implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;
    public static final char[] AccessMask = "RWXLCD".toCharArray();

    public static final long A_NONE = 0x0000;
    public static final long A_READ = 0x0001;
    public static final long A_WRITE = 0x0002;
    public static final long A_EXECUTE = 0x0004;
    public static final long A_LIST = 0x0008;
    public static final long A_CREATE = 0x0010;
    public static final long A_DELETE = 0x0020;

    // aggregated aliases
    private static long[] ALL = new long[]{A_READ, A_WRITE, A_EXECUTE, A_CREATE, A_DELETE, A_LIST};
    public static final long A_PREDEFINED = A_READ | A_WRITE | A_EXECUTE | A_CREATE | A_DELETE | A_LIST;
    public static final long A_CRUD = A_READ | A_WRITE | A_CREATE | A_DELETE | A_LIST;
    public static final long A_PROC = A_EXECUTE | A_LIST;
    long defaultAccess = A_NONE;

    Map<String, Long> access = new HashMap<>();

    public APIAccess() {
    }

    public APIAccess(long access) {
        defaultAccess = access;
    }

    public APIAccess set(String name, long access) {
        if (name == null) {
            defaultAccess = access;
            return this;
        }
        if (this.access == null) {
            this.access = new HashMap<>();
        }
        this.access.put(name, access);
        return this;
    }

    public long get(String name) {
        if (access == null) {
            return defaultAccess;
        }
        Long r = access.get(name);
        return (r != null) ? r : defaultAccess;
    }

    public boolean hasACL(){
        return access!=null && !access.isEmpty();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()));
        sb.append('{');
        sb.append("default=");
        sb.append(toMask(defaultAccess));
        if (hasACL()) {
            sb.append(", acl[");
            sb.append(access.size());
            sb.append("]");
            for (Entry<String, Long> a : access.entrySet()) {
                sb.append("\n  [");
                sb.append(toMask(a.getValue()));
                sb.append("] ");
                sb.append(a.getKey());
            }
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    public String toMask(long access) {
        char[] ch = Arrays.copyOf(AccessMask, AccessMask.length);
        int off = 0;
        for (long l : ALL) {
            if ((l & access) == 0) {
                ch[off] = '-';
            }
            off++;
        }
        return new String(ch);
    }
}
