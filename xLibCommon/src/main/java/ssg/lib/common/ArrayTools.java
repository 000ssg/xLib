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

import java.util.Arrays;

/**
 *
 * @author 000ssg
 */
public class ArrayTools {

    public static <T> T[] merge(T[] a, T... b) {
        if (b == null || b.length == 0) {
            return a;
        }
        if (a == null || a.length == 0 && b != null) {
            return b;
        }
        for (T tb : b) {
            boolean found = false;
            for (T ta : a) {
                if (tb == null && ta == null) {
                    found = true;
                    break;
                }
                if (tb != null && tb.equals(ta)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                a = Arrays.copyOf(a, a.length + 1);
                a[a.length - 1] = tb;
            }
        }
        return a;
    }

    public static <T> T[] intersect(T[] a, T... b) {
        if (b == null || b.length == 0) {
            return a != null ? Arrays.copyOf(a, 0) : null;
        }
        if (a == null || a.length == 0 && b != null) {
            return b != null ? Arrays.copyOf(b, 0) : null;
        }
        T[] r = Arrays.copyOf(a, 0);
        for (T tb : b) {
            boolean found = false;
            for (T ta : a) {
                if (tb == null && ta == null) {
                    found = true;
                    break;
                }
                if (tb != null && tb.equals(ta)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                r = Arrays.copyOf(r, r.length + 1);
                r[r.length - 1] = tb;
            }
        }
        return r;
    }

    public static <T> T[] subtract(T[] a, T... b) {
        if (b == null || b.length == 0) {
            return a != null ? Arrays.copyOf(a, 0) : null;
        }
        if (a == null || a.length == 0 && b != null) {
            return b != null ? Arrays.copyOf(b, 0) : null;
        }
        T[] r = Arrays.copyOf(a, 0);
        for (T ta : a) {
            boolean found = false;
            for (T tb : b) {
                if (tb == null && ta == null) {
                    found = true;
                    break;
                }
                if (tb != null && tb.equals(ta)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                r = Arrays.copyOf(r, r.length + 1);
                r[r.length - 1] = ta;
            }
        }
        return r;
    }
}
