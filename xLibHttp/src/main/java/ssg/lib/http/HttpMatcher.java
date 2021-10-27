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
package ssg.lib.http;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import ssg.lib.common.WildcardMatcher;
import ssg.lib.common.buffers.BufferTools;

/**
 * Request matcher provides means of request URI and other properties (e.g.
 * contentType) matching against reference URI with path/query parameters
 * resolution.
 *
 * Empty path represents root -> "/"
 */
public class HttpMatcher implements Cloneable {

    String scheme;
    String path;
    int port;

    String[] paths; // query text splitted by "/" up to parameter or "file name"
    WildcardMatcher[] wildcards;
    boolean lastIsFile = false;
    boolean absolutePath = false;
    boolean pathIsPrefix = false;
    String ext;
    String[][] qpm;
    String[] methods;
    private String contentType;
    int pathParamCount = 0;

    // temporary var to allow relative path evaluation with respect to parent...
    transient ThreadLocal<HttpMatcher> parent = new ThreadLocal<>();

    public HttpMatcher() {
    }

    /**
     * Returns copy of this HttpMatcher with all paths appended and terminator
     * as in appended matcher.
     *
     * Note: only path/wildcard info is taken from base item, all other info -
     * from child.
     *
     * @param child
     * @return
     */
    public HttpMatcher append(HttpMatcher child) {
        HttpMatcher r = child.copy();
        r.absolutePath = this.absolutePath;
        r.path = (path != null ? path : "") + (r.path.startsWith("/") || path != null && path.endsWith("/") ? "" : "/") + r.path;
        if (paths != null) {
            r.paths = new String[r.paths.length + paths.length];
            for (int i = 0; i < paths.length; i++) {
                r.paths[i] = paths[i];
            }
            int off = paths.length;
            for (int i = 0; i < child.paths.length; i++) {
                r.paths[i + off] = child.paths[i];
            }
            if (r.wildcards != null || wildcards != null) {
                r.wildcards = new WildcardMatcher[r.paths.length + paths.length];

                if (wildcards != null) {
                    for (int i = 0; i < wildcards.length; i++) {
                        r.wildcards[i] = wildcards[i];
                    }
                }
                if (child.wildcards != null) {
                    for (int i = 0; i < child.wildcards.length; i++) {
                        r.wildcards[i + off] = child.wildcards[i];
                    }
                }
            }
        }

        return r;
    }

    public HttpMatcher copy() {
        try {
            HttpMatcher copy = (HttpMatcher) this.clone();
            if (paths != null) {
                copy.paths = Arrays.copyOf(paths, paths.length);
            }
            if (wildcards != null) {
                copy.wildcards = Arrays.copyOf(wildcards, wildcards.length);
            }
            if (qpm != null) {
                copy.qpm = Arrays.copyOf(qpm, qpm.length);
            }
            if (methods != null) {
                copy.methods = Arrays.copyOf(methods, methods.length);
            }
            return copy;
        } catch (CloneNotSupportedException cnsex) {
            return null;
        }
    }

    public HttpMatcher(String path, String... methods) {
        this.path = path;
        this.methods = BufferTools.getNonNulls(methods);
        init();
    }

    public HttpMatcher(URI uri, String... methods) {
        this.path = uri.toString();
        {
            int idx = path.indexOf("//");
            path = path.substring(idx + 1);
        }
        this.methods = BufferTools.getNonNulls(methods);
        scheme = uri.getScheme();
        int port = uri.getPort();
        init();
    }

    public void setPathIsPrefix(boolean pathIsPrefix) {
        this.pathIsPrefix = pathIsPrefix;
    }

    public boolean pathIsPrefix() {
        return pathIsPrefix;
    }

    void noPath() {
        // clear path-based evaluables
        paths = null;
        wildcards = null;
        lastIsFile = false;
        absolutePath = false;
        ext = null;
        qpm = null;
        pathParamCount = 0;
    }

    /**
     * Read-only copy of paths...I
     *
     * @return
     */
    public String[] getPathItems() {
        return Arrays.copyOf(paths, paths.length);
    }

    public void init() {
        if (path != null) {
            noPath();
            absolutePath = path.startsWith("/");
            String s1 = (absolutePath) ? path.substring(1) : path;
            String s2 = null;
            int qIdx = s1.indexOf('?');
            if (qIdx != -1) {
                s2 = s1.substring(qIdx + 1);
                s1 = s1.substring(0, qIdx);
            }

            boolean pathOnly = s1.endsWith("/");
            if (pathOnly) {
                s1 = s1.substring(0, s1.length() - 1);
            }
            // parse the path
            paths = (s1.length() > 0) ? s1.split("/") : null;
            lastIsFile = paths != null && !pathOnly;
            if (s2 != null) {
                String[] ss = s2.split("&");
                qpm = new String[ss.length][];
                for (int i = 0; i < ss.length; i++) {
                    String s = ss[i];
                    int idx = s.indexOf("=");
                    if (idx == -1) {
                        qpm[i] = new String[]{s};
                    } else {
                        qpm[i] = new String[]{s.substring(0, idx), s.substring(idx + 1)};
                    }
                }
            }
            if (paths != null) {
                for (int i = 0; i < paths.length; i++) {
                    if (paths[i].contains("*")) {
                        if (wildcards == null) {
                            wildcards = new WildcardMatcher[paths.length];
                        }
                        wildcards[i] = new WildcardMatcher(paths[i], false);
                    }
                }
            }
            if (lastIsFile && paths[paths.length - 1].contains(".")) {
                ext = paths[paths.length - 1].substring(paths[paths.length - 1].indexOf(".") + 1);
            }
            if (paths != null) {
                for (int i = 0; i < paths.length; i++) {
                    if (paths[i].startsWith("{") && paths[i].endsWith("}")) {
                        pathParamCount++;
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("\n  path='" + path + "'");
        if (paths != null) {
            sb.append("\n  paths=" + paths.length + ", has file: " + lastIsFile + ", is absolute: " + absolutePath + ", is prefix: " + pathIsPrefix);
//            for (String p : paths) {
//                sb.append("\n    '" + p + "'");
//            }
        }
        ;
        if (lastIsFile) {
            sb.append("\n  file=" + paths[paths.length - 1]);
        }
        if (ext != null) {
            sb.append("\n  ext=" + ext);
        }
        if (qpm != null) {
            sb.append("\n  qpm=" + qpm.length);
            for (String[] qp : qpm) {
                sb.append("\n    '" + qp[0] + "'");
                if (qp.length > 1) {
                    sb.append("='" + qp[1] + "'");
                }
            }
        }
        if (methods != null && methods.length > 0) {
            sb.append("\n  methods=" + methods.length);
            for (String m : methods) {
                sb.append("  '" + m + "'");
            }
        }
        if (getContentType() != null) {
            sb.append("\n  contentType=" + getContentType());
        }
        sb.append("\n}");
        return sb.toString();
    }

    public boolean hasQueryPathParameter(String name) {
        if (qpm != null && qpm.length > 0) {
            for (int i = 0; i < qpm.length; i++) {
                if (qpm[i][0].equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getQueryPathParameter(String name) {
        if (qpm != null && qpm.length > 0) {
            for (int i = 0; i < qpm.length; i++) {
                if (qpm[i][0].equals(name)) {
                    return qpm[i].length > 1 ? qpm[i][1] : null;
                }
            }
        }
        return null;
    }

    public Map<String, Object> getParameters(HttpMatcher rm, boolean pathOnly) throws IOException {
        Map<String, Object> r = new LinkedHashMap<>();
        if (pathParamCount > 0) {
            for (int i = 0; i < paths.length; i++) {
                if (paths[i].startsWith("{")) {
                    String key = paths[i].substring(1, paths[i].length() - 1);
                    String nv = rm.paths[i];
                    if (r.containsKey(key)) {
                        Object kv = r.get(key);
                        if (kv instanceof String) {
                            r.put(key, new String[]{(String) kv, nv});
                        } else if (kv instanceof String[]) {
                            String[] ss = (String[]) kv;
                            ss = Arrays.copyOf(ss, ss.length + 1);
                            ss[ss.length - 1] = nv;
                            r.put(key, ss);
                        } else {
                            throw new IOException("Unexpected non-string parameter values: " + key + "=" + kv + " -> " + nv);
                        }
                    } else {
                        r.put(key, nv);
                    }
                }
            }
        }
        if (!pathOnly) {
            if (rm != null && rm.qpm != null) {
                for (int i = 0; i < rm.qpm.length; i++) {
                    // TODO: no value ? null or "" ???
                    String key = rm.qpm[i][0];
                    String nv = (rm.qpm[i].length > 1) ? rm.qpm[i][1] : "";
                    if (r.containsKey(key)) {
                        Object kv = r.get(key);
                        if (kv instanceof String) {
                            r.put(key, new String[]{(String) kv, nv});
                        } else if (kv instanceof String[]) {
                            String[] ss = (String[]) kv;
                            ss = Arrays.copyOf(ss, ss.length + 1);
                            ss[ss.length - 1] = nv;
                            r.put(key, ss);
                        } else {
                            throw new IOException("Unexpected non-string parameter values: " + key + "=" + kv + " -> " + nv);
                        }
                    } else {
                        r.put(key, nv);
                    }
                }
            }
            if (qpm != null) {
                for (int i = 0; i < qpm.length; i++) {
                    if (qpm[i][0] != null) {
                        String key = qpm[i][0];
                        if (qpm[i][0].startsWith("{")) {
                            key = key.substring(1, key.length() - 1);
                        }

                        String nv = qpm[i][1];
                        if (r.containsKey(key)) {
                            Object kv = r.get(key);
                            if (kv instanceof String) {
                                r.put(key, new String[]{(String) kv, nv});
                            } else if (kv instanceof String[]) {
                                String[] ss = (String[]) kv;
                                ss = Arrays.copyOf(ss, ss.length + 1);
                                ss[ss.length - 1] = nv;
                                r.put(key, ss);
                            } else {
                                throw new IOException("Unexpected non-string parameter values: " + key + "=" + kv + " -> " + nv);
                            }
                        } else {
                            r.put(key, nv);
                        }
                    }
                }
            }
        }
        return r;
    }

    public String getPath() {
        return path;
    }

    /**
     * Evaluates matching level for provided URI representation with optional
     * parent matcher (for use with relative path only!)
     *
     * @param parent
     * @param rm
     * @return
     */
    public float match(HttpMatcher parent, HttpMatcher rm) {
        synchronized (this) {
            this.parent.set(parent);
            try {
                return match(rm);
            } finally {
                this.parent.set(null);
            }
        }
    }

    /**
     * Evaluates matching level for provided URI representation.
     *
     * @param rm
     * @return
     */
    public float match(HttpMatcher rm) {
        if (rm == null || rm.paths == null && !"/".equals(rm.path)) {
            return 0;
        }

        if (methods != null && methods.length > 0) {
            if (rm.methods == null) {
                return 0;
            }
            boolean found = false;
            for (String m : methods) {
                for (String m2 : rm.methods) {
                    if (m.equalsIgnoreCase(m2)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            if (!found) {
                return 0;
            }
        }

        // check paths, ext, contentType, params (path+qpm)
        float[] weights = new float[]{
            (paths != null && paths.length > 0 || "/".equals(path)) ? paths != null && paths.length == 1 && lastIsFile ? pathIsPrefix || path.equals(rm.path) ? 1 : 0 : 1 : 0,
            (ext != null) ? 1 : 0,
            (getContentType() != null) ? 1 : 0
        };

        float max = 0;
        for (float f : weights) {
            max += f;
        }
        if (max == 0) {
            return 0;
        }

        float cur = weights[0] * matchPath(rm);
        if (weights[0] == 0 || cur > 0) {
            cur += weights[1] * matchExt(rm);
        }
        if (weights[0] == 0 && weights[1] == 0 || cur > 0) {
            cur += weights[2] * matchContentType(rm);
        }

        return cur / max;
    }

    public float matchPath(HttpMatcher rm) {
        float f = 0;

        if (absolutePath) {
            if (paths == null) {
                // this is root - any path will match...
                f = 1;
            } else if (rm.paths == null) {
                f = 0;
            } else {
                // check length match
                float f0 = (paths.length == rm.paths.length) ? 1 : 0.5f;
                float f1 = 0;
                for (int i = 0; i < Math.min(paths.length, rm.paths.length); i++) {
                    if (paths[i].startsWith("{")) {
                        f1 += (1f / paths.length);
                    } else if (paths[i].equals(rm.paths[i])) {
                        f1 += (1f / paths.length);
                    } else if (wildcards != null && wildcards[i] != null) {
                        WildcardMatcher m = wildcards[i];
                        f1 += (m.match(rm.paths[i]) / paths.length);
                    } else {
                        break;
                    }
                }
                f = ((f1 >= 0.99f) ? (f0 + f1) / 2 : 0);
            }
        } else if (parent.get() != null) {
            HttpMatcher p = parent.get();
            if (p.paths != null && p.paths.length > 0) {
                if (paths == null) {
                    // this is root - any path will match...
                    f = 1;
                } else if (rm.paths == null) {
                    f = 0;
                } else {
                    // check length match
                    int ppl = p.paths.length;
                    float f0 = (paths.length + ppl == rm.paths.length) ? 1 : 0.5f;
                    float f1 = 0;
                    for (int i = 0; i < Math.min(paths.length + ppl, rm.paths.length); i++) {
                        HttpMatcher pi = i < ppl ? p : this;
                        int off = i < ppl ? i : i - ppl;
                        if (pi.paths[off].startsWith("{")) {
                            f1 += (1f / (ppl + paths.length));
                        } else if (pi.paths[off].equals(rm.paths[i])) {
                            f1 += (1f / (paths.length + ppl));
                        } else if (pi.wildcards != null && pi.wildcards[off] != null) {
                            WildcardMatcher m = pi.wildcards[off];
                            f1 += (m.match(rm.paths[i]) / paths.length);
                        } else {
                            if (pathIsPrefix) {
                                // if prefix all the rest path matches...
                            } else {
                                f0 = 0;
                                f1 = 0;
                            }
                            break;
                        }
                    }
                    f = ((f1 >= 0.99f) ? (f0 + f1) / 2 : 0);
                }
            }
        }
        return f;
    }

    /**
     * Simple extension match.
     *
     * @param rm
     * @return
     */
    public float matchExt(HttpMatcher rm) {
        float f = 0;
        if (ext != null && ext.equals(rm.ext)) {
            f = 1;
        }
        return f;
    }

    public float matchContentType(HttpMatcher rm) {
        if (contentType != null) {
            if (rm.contentType != null) {
                return (contentType.equalsIgnoreCase(rm.contentType))
                        ? 1f
                        : ((rm.contentType.contains(contentType)))
                        ? 0.5f
                        : 0f;
            }
        }
        return 0f;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + (this.path != null ? this.path.hashCode() : 0);
        hash = 19 * hash + (this.getContentType() != null ? this.getContentType().hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HttpMatcher other = (HttpMatcher) obj;
        if ((this.path == null) ? (other.path != null) : !this.path.equals(other.path)) {
            return false;
        }
        if ((this.getContentType() == null) ? (other.getContentType() != null) : !this.contentType.equals(other.contentType)) {
            return false;
        }
        return true;
    }

    /**
     * @return the contentType
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @param contentType the contentType to set
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public static class HttpMatcherComposite extends HttpMatcher {

        Collection<HttpMatcher> matchers = new LinkedHashSet<>();

        public HttpMatcherComposite() {
        }

        public HttpMatcherComposite(HttpMatcher... matchers) {
            if (matchers != null) {
                this.add(matchers);
            }
        }

        public HttpMatcherComposite add(HttpMatcher... matchers) {
            if (matchers != null) {
                for (HttpMatcher hm : matchers) {
                    if (hm != null) {
                        this.matchers.add(hm);
                    }
                }
            }
            return this;
        }

        public float match(HttpMatcher parent, HttpMatcher rm) {
            synchronized (this) {
                for (HttpMatcher lm : matchers()) {
                    lm.parent.set(parent);
                }
                try {
                    return super.match(parent, rm);
                } finally {
                    for (HttpMatcher lm : matchers()) {
                        lm.parent.set(null);
                    }
                }
            }
        }

        @Override
        public float match(HttpMatcher rm) {
            float f = 0;
            for (HttpMatcher lm : matchers()) {
                float fl = lm.match(rm);
                if (fl > f) {
                    f = fl;
                }
            }
            return f;
        }

        public HttpMatcher matching(HttpMatcher rm) {
            HttpMatcher result = null;
            float f = 0;
            for (HttpMatcher lm : matchers()) {
                float fl = lm.match(rm);
                if (fl > f) {
                    f = fl;
                    result = lm;
                }
            }
            return result;
        }

        public Collection<HttpMatcher> matchers() {
            return matchers;
        }
    }
}
