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

import java.util.Arrays;

/**
 * Listeners helper.
 *
 * @author 000ssg
 */
public class LS<T> {

    T[] listeners;

    public LS(T[] base) {
        set(base);
    }

    public boolean isEmpty() {
        return listeners.length == 0;
    }

    public int size() {
        return listeners.length;
    }
    
    public T[] get() {
        return listeners;
    }

    public LS<T> set(T... ls) {
        if (ls != null) {
            this.listeners = ls;
        } else if (listeners != null) {
            listeners = Arrays.copyOf(listeners, 0);
        }
        return this;
    }

    public LS<T> add(T... ls) {
        if (ls != null) {
            for (T l : ls) {
                if (l != null) {
                    synchronized (this) {
                        boolean isNew = true;
                        for (T li : listeners) {
                            if (li.equals(l)) {
                                isNew = false;
                                break;
                            }
                        }
                        if (isNew) {
                            listeners = Arrays.copyOf(listeners, listeners.length + 1);
                            listeners[listeners.length - 1] = l;
                        }
                    }
                }
            }
        }
        return this;
    }

    public void remove(T... ls) {
        if (ls != null) {
            for (T l : ls) {
                if (l != null) {
                    synchronized (this) {
                        int idx = -1;
                        for (int i = 0; i < listeners.length; i++) {
                            if (l.equals(listeners[i])) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx != -1) {
                            for (int i = 0; i < listeners.length; i++) {
                                if (i <= idx) {
                                    continue;
                                }
                                listeners[i - 1] = listeners[i];
                            }
                            listeners = Arrays.copyOf(listeners, listeners.length - 1);
                        }
                    }
                }
            }
        }
    }

}
