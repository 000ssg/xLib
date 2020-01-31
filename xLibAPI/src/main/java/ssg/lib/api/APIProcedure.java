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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author 000ssg
 */
public class APIProcedure extends APIItem {

    public static final long PO_STATIC = 0x0001; // set if procedure call does not need context
    public static final long PO_LONG = 0x0002; // set if procedure call may take long time.
    public static final long PO_LARGE = 0x0004; // set if procedure call may take/produce large amount of data
    public static final long PO_OPTIONAL = 0x0008; // set if procedure may be present OR missing in the API?
    public static final long PO_DEPRECATED = 0x0010; // set if procedure will/may be removed in following revisions

    private static final long serialVersionUID = 1L;
    public Map<String, APIParameter> params = new LinkedHashMap<>();
    public long options;

    public APIProcedure(String name, String... scope) {
        super(APIItemCategory.procedure, name, scope);
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);
        for (Collection coll : new Collection[]{params.values()}) {
            if (coll != null) {
                for (Object obj : coll) {
                    if (obj == null) {
                        continue;
                    }
                    APIItem[] items = (obj instanceof APIItem) ? new APIItem[]{(APIItem) obj} : (APIItem[]) obj;
                    for (APIItem item : items) {
                        if (item != null) {
                            item.fixUsedIn(this);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.delete(sb.length() - 1, sb.length());
        sb.append(", params=" + params.size());
        if (!params.isEmpty()) {
            sb.append("\n  Parameters:");
            for (Map.Entry<String, APIParameter> t : params.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!params.isEmpty()) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        if (!params.isEmpty()) {
            sb.append("\n  Parameters[" + params.size() + "]");
            for (Map.Entry<String, APIParameter> t : params.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        return sb.toString();
    }

    /**
     * Verify parameters match level.
     *
     * If missing mandatory - 0;
     *
     * Parameter is missing if it is mandatory and is not present or is present
     * but is null but data type does not allow null.
     *
     * Returns parameters match level (max - 1). If Extra parameters present
     * (unhandled) - returns negated match level.
     *
     * @param params
     * @return
     */
    public float testParameters(Map<String, Object> params) {
        // no expected, no provided -> 100% (1f)
        if ((params == null || params.isEmpty()) && this.params.isEmpty()) {
            return 1f;
        }

        float p0 = 0;
        float p1 = 0; // mandatory +/-
        float p2 = 0; // optional
        float p3 = 0; // unused /excessive

        // check from procedure point of view
        for (Entry<String, APIParameter> pe : this.params.entrySet()) {
            APIParameter p = pe.getValue();
            boolean present = params != null && params.containsKey(pe.getKey());
            Object value = (present) ? params.get(pe.getKey()) : null;
            if (p.mandatory) {
                p0++;
                if (present) {
                    p1++;
                    if (value == null && p.type.mandatory) {
                        p1--;
                    }
                } else {
                    p1--;
                }
            } else {
                if (present) {
                    p2++;
                }
            }
        }

        // check from parameters point of view
        if (params != null) {
            for (String pn : params.keySet()) {
                if (!this.params.containsKey(pn)) {
                    p3++;
                }
            }
        }

        // missing mandatory -> no match
        if (p1 < p0) {
            return 0f;
        }

        // evaluate weighted signed match
        float pp = p1 + p2;
        if (this.params.size() > 0) {
            pp /= this.params.size();
        } else {
            pp = 0f;
        }
        return (p3 > 0) ? -pp : pp;
    }

    /**
     * Parameter order-based verification.
     *
     * @param params
     * @return
     */
    public float testParameters(List params) {
        // no expected, no provided -> 100% (1f)
        if ((params == null || params.isEmpty()) && this.params.isEmpty()) {
            return 1f;
        }

        float p0 = 0;
        float p1 = 0; // mandatory +/-
        float p2 = 0; // optional
        float p3 = 0; // unused /excessive

        // check from procedure point of view
        for (Entry<String, APIParameter> pe : this.params.entrySet()) {
            APIParameter p = pe.getValue();
            boolean present = (params != null && p.order != null && p.order <= params.size()) ? true : false;
            Object value = (present) ? params.get(p.order - 1) : null;
            if (p.mandatory) {
                p0++;
                if (present) {
                    p1++;
                    if (value == null && p.type.mandatory) {
                        p1--;
                    }
                } else {
                    p1--;
                }
            } else {
                if (present) {
                    p2++;
                }
            }
        }

        // check from parameters point of view
        p3 = (params != null) ? params.size() - this.params.size() : 0;

        // missing mandatory -> no match
        if (p1 < p0) {
            return 0;
        }

        // evaluate weighted signed match
        return (p3 > 0) ? -(p1 + p2) / this.params.size() : (p1 + p2) / this.params.size();
    }

    public float testParameters(Object[] params) {
        return testParameters((params != null) ? Arrays.asList(params) : (List) null);
    }

    public List toParametersList(Map<String, Object> params) {
        Object[] r = toParametersArray(params);
        if (r != null) {
            return Arrays.asList(r);
        } else {
            return null;
        }
    }

    public Object[] toParametersArray(Map<String, Object> params) {
        if (testParameters(params) != 0f) {
            Object[] r = new Object[this.params.size()];
            int off = 0;
            for (Entry<String, APIParameter> pe : this.params.entrySet()) {
                APIParameter p = pe.getValue();
                boolean present = (params != null && p.order != null && p.order <= params.size()) ? true : false;
                Object value = (present) ? params.get(p.order - 1) : null;
                if (present) {
                    r[off] = value;
                }
                off++;
            }
            return r;
        } else {
            return null;
        }
    }

    public Map<String, Object> toParametersMap(Object... params) {
        return toParametersMap(params != null && params.length > 0 ? Arrays.asList(params) : (List) null);
    }

    public Map<String, Object> toParametersMap(List params) {
        if (testParameters(params) != 0f) {
            if (params == null || params.isEmpty()) {
                return null;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            // check from procedure point of view
            for (Entry<String, APIParameter> pe : this.params.entrySet()) {
                APIParameter p = pe.getValue();
                boolean present = (params != null && p.order != null && p.order <= params.size()) ? true : false;
                Object value = (present) ? params.get(p.order - 1) : null;
                if (present) {
                    r.put(pe.getKey(), value);
                }
            }
            return r;
        } else {
            return null;
        }
    }

}
