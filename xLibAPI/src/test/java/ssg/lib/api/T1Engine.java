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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author sesidoro
 */
public class T1Engine {

    Map<String, List<T1>> a = new LinkedHashMap<>();
    Map<String, List<T1>> b = new LinkedHashMap<>();
    Map<String, List<T1>> c = new LinkedHashMap<>();

    public T1 first(String a, String b, String c) {
        T1[] tt = get(a, b, c);
        if (tt != null && tt.length > 0) {
            return tt[0];
        }
        return null;
    }

    public T1[] get(String a, String b, String c) {
        List<T1> tt = find(a, b, c);
        return tt.toArray(new T1[tt.size()]);
    }

    public List<T1> find(String a, String b, String c) {
        List<T1> tt = new ArrayList<>();

        List<T1> aa = null;
        List<T1> bb = null;
        List<T1> cc = null;

        if (a != null) {
            aa = this.a.get(a);
        }
        if (b != null) {
            bb = this.b.get(b);
        }
        if (c != null) {
            cc = this.c.get(c);
        }

        if ((a != null && (aa == null || aa.isEmpty()))
                || (b != null && (bb == null || bb.isEmpty()))
                || (c != null && (cc == null || cc.isEmpty()))) {
            return null;
        }

        Collection<T1> all = new LinkedHashSet<>();
        if (aa != null) {
            all.addAll(aa);
        }
        if (bb != null) {
            all.addAll(bb);
        }
        if (cc != null) {
            all.addAll(cc);
        }

        for (T1 t : all) {
            if ((aa == null || aa.contains(t))
                    && (bb == null || bb.contains(t))
                    && (cc == null || cc.contains(t))) {
                if (!tt.contains(t)) {
                    tt.add(t);
                }
            }
        }
        return tt;
    }

    public void add(T1... ts) {
        if (ts != null) {
            for (T1 t : ts) {
                if (t.a != null) {
                    List<T1> aa = a.get(t.a);
                    if (aa == null) {
                        aa = new ArrayList<>();
                        a.put(t.a, aa);
                    }
                    if (!aa.contains(t)) {
                        aa.add(t);
                    }
                }
                if (t.b != null) {
                    List<T1> bb = b.get(t.b);
                    if (bb == null) {
                        bb = new ArrayList<>();
                        b.put(t.b, bb);
                    }
                    if (!bb.contains(t)) {
                        bb.add(t);
                    }
                }
                if (t.c != null) {
                    List<T1> cc = c.get(t.c);
                    if (cc == null) {
                        cc = new ArrayList<>();
                        c.put(t.c, cc);
                    }
                    if (!cc.contains(t)) {
                        cc.add(t);
                    }
                }
            }
        }
    }

    public static T1Engine create(String[]  
        ... abcs) {
        T1Engine r = new T1Engine();
        if (abcs != null) {
            for (String[] abc : abcs) {
                r.add(new T1(abc));
            }
        }
        return r;
    }

    public static class T1 {

        static AtomicInteger NEXT_ID = new AtomicInteger(1);

        int id = NEXT_ID.getAndIncrement();
        public String a;
        public String b;
        public String c;

        public T1() {
        }

        public T1(String... abc) {
            if (abc != null) {
                a = (abc.length > 0) ? abc[0] : null;
                b = (abc.length > 1) ? abc[1] : null;
                c = (abc.length > 2) ? abc[2] : null;
            }
        }

        public T1(
                String a,
                String b,
                String c
        ) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public String toString() {
            return "T1{" + id
                    + ((a != null) ? ", a=" + a : "")
                    + ((b != null) ? ", b=" + b : "")
                    + ((c != null) ? ", c=" + c : "")
                    + '}';
        }

        public static T1 random() {
            int idx = (int) (Math.random() * 3);

            String[] abc = new String[idx];
            int c = 0;
            for (int i = 0; i < abc.length; i++) {
                if (Math.random() > 0.25) {
                    abc[i] = "" + ((char) (((int) 'A') + (int) ((Math.random() * 24))));
                    c++;
                }
            }
            if (c == 0) {
                idx = (int) (Math.random() * abc.length);
                if (idx >= abc.length) {
                    idx = abc.length - 1;
                }
                if (idx >= 0) {
                    abc[idx] = "" + ((char) (((int) 'a') + (int) ((Math.random() * 24))));
                }
            }
            return new T1(abc);
        }

        public static List<T1> random(int count) {
            List<T1> r = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                T1 t = random();
                r.add(t);
            }
            return r;
        }
    }
}
