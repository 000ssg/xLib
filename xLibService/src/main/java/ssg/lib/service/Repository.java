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
package ssg.lib.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import ssg.lib.common.Matcher;
import ssg.lib.common.buffers.BufferTools;

/**
 * Repository represents collection of items with add/remove functionality and
 * search capability.
 *
 * @author 000ssg
 */
public class Repository<T> {

    List<T> items = new ArrayList<>();
    List<RepositoryListener<T>> listeners = new ArrayList<>();

    public Repository() {
    }

    public Repository(T... items) {
        if (items != null) {
            this.addItem(0, items);
        }
    }

    public Repository(RepositoryListener<T> listener, T... items) {
        this.addRepositoryListener(listener);
        if (items != null) {
            this.addItem(0, items);
        }
    }

    public void addRepositoryListener(RepositoryListener<T>... ls) {
        if (ls != null) {
            for (RepositoryListener<T> l : ls) {
                if (l != null && !listeners.contains(l)) {
                    listeners.add(l);
                }
            }
        }
    }

    public void removeRepositoryListener(RepositoryListener<T>... ls) {
        if (ls != null) {
            for (RepositoryListener<T> l : ls) {
                if (l != null && listeners.contains(l)) {
                    listeners.remove(l);
                }
            }
        }
    }

    public List<T> items() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Invoked when item is added to repository
     *
     * @param item
     */
    public void onAdded(T item) {
        if (listeners != null) {
            for (RepositoryListener<T> l : listeners) {
                l.onAdded(item);
            }
        }
    }

    /**
     * Invoked when item is removed from repository
     *
     * @param item
     */
    public void onRemoved(T item) {
        if (listeners != null) {
            for (RepositoryListener<T> l : listeners) {
                l.onRemoved(item);
            }
        }
    }

    /**
     * Invoked when item is found for a match. If top is true - the item is
     * first in sorted matched list.
     *
     * @param item
     * @param level
     * @param matcher
     * @param top
     */
    public void onFound(Matched<T> matched, Matcher<T> matcher, boolean top) {
    }

    /**
     * Final match
     *
     * @param matched
     * @param matcher
     */
    public void onFound(Matched<T>[] matched, Matcher<T> matcher) {
    }

    public int size() {
        return items.size();
    }

    public boolean addItem(T sp) {
        return addItem(sp, items.size());
    }

    public boolean addItem(T sp, int order) {
        if (sp != null && !items.contains(sp)) {
            if (order >= 0 && order < items.size()) {
                items.add(order, sp);
                onAdded(sp);
            } else {
                items.add(sp);
                onAdded(sp);
            }
            return true;
        }
        return false;
    }

    public int addItem(int order, T... sps) {
        int c = 0;
        if (sps != null && sps.length > 0) {
            if (order >= 0 && order < items.size()) {
                for (int i = sps.length - 1; i >= 0; i--) {
                    if (addItem(sps[i], order)) {
                        c++;
                    }
                }
            } else {
                for (T sp : sps) {
                    if (addItem(sp)) {
                        c++;
                    }
                }
            }
        }
        return c;
    }

    public int addItem(int order, List<T>... sps) {
        int c = 0;
        if (sps != null && sps.length > 0) {
            if (order >= 0 && order < items.size()) {
                for (int i = sps.length - 1; i >= 0; i--) {
                    if (sps[i] == null || sps[i].isEmpty()) {
                        continue;
                    }
                    for (int j = sps[i].size() - 1; j >= 0; j--) {
                        if (addItem(sps[i].get(j), order)) {
                            c++;
                        }
                    }
                }
            } else {
                for (List<T> lst : sps) {
                    if (lst != null) {
                        for (T sp : lst) {
                            if (addItem(sp)) {
                                c++;
                            }
                        }
                    }
                }
            }
        }
        return c;
    }

    public boolean removeItem(T sp) {
        if (sp != null && items.contains(sp)) {
            items.remove(sp);
            onRemoved(sp);
            return true;
        }
        return false;
    }

    public int removeItem(T... sps) {
        int c = 0;
        if (sps != null && sps.length > 0) {
            for (T sp : sps) {
                if (removeItem(sp)) {
                    c++;
                }
            }
        }
        return c;
    }

    public int removeItem(List<T>... sps) {
        int c = 0;
        if (sps != null && sps.length > 0) {
            for (List<T> lst : sps) {
                if (lst != null) {
                    for (T sp : lst) {
                        if (removeItem(sp)) {
                            c++;
                        }
                    }
                }
            }
        }
        return c;
    }

    public void clear() {
        Object[] all = items.toArray();
        items.clear();
        if (all != null) {
            for (Object o : all) {
                onRemoved((T) o);
            }
        }
    }

    /**
     * Returns matched items in the order of highest match level.
     *
     * @param matcher
     * @return
     */
    public List<T> find(Matcher<T> matcher, MatchListener<T> listener) {
        List<T> r = new ArrayList<>();
        if (matcher == null) {
            r.addAll(items);
        } else {
            Matched<T>[] oos = findMatched(matcher, listener, true);
            if (oos != null && oos.length > 0) {
                for (int i = 0; i < oos.length; i++) {
                    r.add(oos[i].getItem());
                }
            }
        }
        return r;
    }

    /**
     * Returns matching items with level optionally order by descending match
     * level.
     *
     * NOTE: if matcher is also listener and no listener is given - it will get
     * matching events
     *
     * @param matcher
     * @param listener
     * @param sort
     * @return
     */
    public Matched<T>[] findMatched(Matcher<T> matcher, MatchListener<T> listener, boolean sort) {
        Matched<T>[] r = null;
        MatchListener<T> l = (listener != null) ? listener : (matcher instanceof MatchListener) ? (MatchListener) matcher : null;
        if (matcher == null) {
            // cannot evaluate matched... -> nothing to return
        } else {
            r = new Matched[items.size()];
            int idx = 0;
            for (T t : items()) {
                float f = matcher.match(t);
                if (f > 0) {
                    Matched<T> m = new Matched<>(t, f);
                    r[idx++] = m;
                    onFound(m, matcher, false);
                    if (l != null) {
                        l.onFound(m, matcher, false);
                    }
                }
            }
            r = Arrays.copyOf(r, idx);
            if (idx > 0) {
                if (sort) {
                    Arrays.sort(r, new Comparator<Matched<T>>() {
                        @Override
                        public int compare(Matched<T> o1, Matched<T> o2) {
                            Float f1 = o1.getLevel();
                            return -f1.compareTo(o2.getLevel());
                        }
                    });
                    onFound(r[0], matcher, true);
                    if (l != null) {
                        l.onFound(r[0], matcher, true);
                    }
                }
            }
        }
        if (l != null) {
            l.onFound(r, matcher);
        }
        return r;
    }

    public static class CombinedRepository<T> extends Repository<T> {

        Repository<T>[] repos;

        public CombinedRepository(Repository<T>... repos) {
            this.repos = new Repository[(repos != null) ? repos.length : 0];
            if (repos != null) {
                int off = 0;
                for (Repository<T> repo : repos) {
                    if (repo != null) {
                        this.repos[off++] = repo;
                    }
                }
                if (off < this.repos.length) {
                    this.repos = Arrays.copyOf(this.repos, off);
                }
            }
        }

        @Override
        public Matched<T>[] findMatched(Matcher<T> matcher, MatchListener<T> listener, boolean sort) {
            Matched<T>[] r = null;
            for (Repository<T> repo : repos) {
                Matched<T>[] ri = repo.findMatched(matcher, listener, sort);
                if (ri != null && ri.length > 0) {
                    if (r == null) {
                        r = ri;
                    } else {
                        int off = r.length;
                        r = Arrays.copyOf(r, off + ri.length);
                        for (int i = 0; i < ri.length; i++) {
                            r[off + i] = ri[i];
                        }
                    }
                }
            }
            return r;
        }

        @Override
        public List<T> find(Matcher<T> matcher, MatchListener<T> listener) {
            return super.find(matcher, listener); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void clear() {
        }

        @Override
        public int removeItem(List<T>... sps) {
            return 0;
        }

        @Override
        public int removeItem(T... sps) {
            return 0;
        }

        @Override
        public boolean removeItem(T sp) {
            return false;
        }

        @Override
        public int addItem(int order, List<T>... sps) {
            return 0;
        }

        @Override
        public int addItem(int order, T... sps) {
            return 0;
        }

        @Override
        public boolean addItem(T sp, int order) {
            return false;
        }

        @Override
        public boolean addItem(T sp) {
            return false;
        }

        @Override
        public int size() {
            int c = 0;
            for (Repository<T> repo : repos) {
                c += repo.size();
            }
            return c;
        }

        @Override
        public void onFound(Matched<T>[] matched, Matcher<T> matcher) {
        }

        @Override
        public void onFound(Matched<T> matched, Matcher<T> matcher, boolean top) {
        }

        @Override
        public void onRemoved(T item) {
        }

        @Override
        public void onAdded(T item) {
        }

        @Override
        public List<T> items() {
            List<T> r = new ArrayList<>();
            for (Repository<T> repo : repos) {
                r.addAll(repo.items);
            }
            return r;
        }

    }

    public static class Matched<T> {

        private T item;
        private float level;
        Object[] parameters;

        public Matched() {
        }

        public Matched(T item, float level) {
            this.item = item;
            this.level = level;
        }

        /**
         * @return the item
         */
        public T getItem() {
            return item;
        }

        /**
         * @param item the item to set
         */
        public void setItem(T item) {
            this.item = item;
        }

        /**
         * @return the level
         */
        public float getLevel() {
            return level;
        }

        /**
         * @param level the level to set
         */
        public void setLevel(float level) {
            this.level = level;
        }
    }

    public static class Matchers<T> implements Matcher<T> {

        float weight = 1;
        Matcher<T>[] matcher;
        float[] weights = new float[matcher.length];
        float wsum = 0;

        public Matchers(Matcher<T>... matcher) {
            this.matcher = BufferTools.getNonNulls(matcher);
            init();
        }

        public Matchers(float weight, Matcher<T>... matcher) {
            this.weight = (weight >= 0) ? weight : 1;
            this.matcher = BufferTools.getNonNulls(matcher);
            init();
        }

        void init() {
            if (matcher != null) {
                weights = new float[matcher.length];
                for (int i = 0; i < matcher.length; i++) {
                    float w = matcher[i].weight();
                    weights[i] = w;
                    wsum += w;
                }
            }
        }

        @Override
        public float match(T t) {
            float w = 0;
            if (matcher == null) {
                return (t != null) ? 1 : 0;
            }
            int idx = 0;
            for (Matcher<T> m : matcher) {
                w += m.match(t) * weights[idx++];
            }
            return w / wsum;
        }

        @Override
        public float weight() {
            return weight;
        }
    }

    public static interface MatchListener<T> {

        void onFound(Matched<T> matched, Matcher<T> matcher, boolean top);

        void onFound(Matched<T>[] matched, Matcher<T> matcher);
    }

    /**
     * Used to provide combined match listening functionality
     *
     * @param <T>
     */
    public static interface ListeningMatcher<T> extends Matcher<T>, MatchListener<T> {
    }

    public static interface RepositoryListener<T> {

        void onAdded(T item);

        void onRemoved(T item);
    }
}
