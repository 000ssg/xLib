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

import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author sesidoro
 */
public class ListSpy<T> extends ArrayList<T> {

    void log(String s) {
        System.out.println("[" + Thread.currentThread().getName() + "]ListSpy(" + size() + "): " + s);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean r = super.removeAll(c);
        log("removeAll(" + c.size() + ") = " + r);
        return r;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean r = super.addAll(index, c);
        log("addAll(" + index + ", " + c.size() + ") = " + r);
        return r;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean r = super.addAll(c);
        log("addAll(" + c.size() + ") = " + r);
        return r;
    }

    @Override
    public boolean remove(Object o) {
        boolean r = super.remove(o);
        log("remove = " + r + " : " + ("" + o).replace("\n", "\n  "));
        return r;
    }

    @Override
    public T remove(int index) {
        T r = super.remove(index);
        log("remove(" + index + ") = " + ("" + r).replace("\n", "\n  "));
        return r;
    }

    @Override
    public void add(int index, T element) {
        super.add(index, element);
        log("add = " + index + " : " + ("" + element).replace("\n", "\n  "));
    }

    @Override
    public boolean add(T e) {
        boolean r = super.add(e);
        log("add = " + r + " : " + ("" + e).replace("\n", "\n  "));
        return r;
    }
}
