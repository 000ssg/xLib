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

import ssg.lib.api.util.APISearchable;
import ssg.lib.api.util.APITools;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public class APIGroup extends APIItem implements APISearchable {

    private static final long serialVersionUID = 1L;
    public Map<String, APIGroup> groups = new LinkedHashMap<>();
    public Map<String, APIProcedure[]> procs = new LinkedHashMap<>();
    public Map<String, APIFunction[]> funcs = new LinkedHashMap<>();
    public Map<String, APIDataType> types = new LinkedHashMap<>();
    public Map<String, APIError> errors = new LinkedHashMap<>();

    APIGroup(APIItemCategory category, String name) {
        super(category, name);
    }

    public APIGroup(String name, String... scope) {
        super(APIItemCategory.group, name, scope);
    }

    /**
     * Provides access to all group items, e.g. for search. Override this item
     * to enable more items in search for extensions.
     *
     * @return
     */
    public Map[] getItemCollections(Class type) {
        return new Map[]{
            (type == null || APIProcedure.class.isAssignableFrom(type)) ? funcs : null,
            (type == null || APIProcedure.class.isAssignableFrom(type)) ? procs : null,
            (type == null || APIDataType.class.isAssignableFrom(type)) ? types : null,
            groups
        };
    }

    @Override
    public <T extends APIItem> Collection<T> find(APIMatcher matcher, Class type, Collection<T> result) {
        return APITools.find(matcher, type, result,
                getItemCollections(type)
        );
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);
        for (Collection coll : new Collection[]{groups.values(), funcs.values(), procs.values(), types.values()}) {
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
        if (!groups.isEmpty()) {
            sb.append("\n  Groups[" + groups.size() + "]:");
            for (Map.Entry<String, APIGroup> t : groups.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!procs.isEmpty()) {
            sb.append("\n  Procedures[" + procs.size() + "]:");
            for (Map.Entry<String, APIProcedure[]> t : procs.entrySet()) {
                sb.append("\n    " + t.getKey() + "(" + t.getValue().length + "): ");
                if (t.getValue().length == 1) {
                    sb.append(t.getValue()[0].toString().replace("\n", "\n      "));
                } else {
                    for (Object f : t.getValue()) {
                        sb.append("\n      " + f.toString().replace("\n", "\n      "));
                    }
                }
            }
        }
        if (!funcs.isEmpty()) {
            sb.append("\n  Functions[" + funcs.size() + "]:");
            for (Map.Entry<String, APIFunction[]> t : funcs.entrySet()) {
                sb.append("\n    " + t.getKey() + "(" + t.getValue().length + "): ");
                if (t.getValue().length == 1) {
                    sb.append(t.getValue()[0].toString().replace("\n", "\n      "));
                } else {
                    for (Object f : t.getValue()) {
                        sb.append("\n      " + f.toString().replace("\n", "\n      "));
                    }
                }
            }
        }
        if (!types.isEmpty()) {
            sb.append("\n  Types[" + types.size() + "]:");
            for (Map.Entry<String, APIDataType> t : types.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!errors.isEmpty()) {
            sb.append("\n  Errors[" + types.size() + "]:");
            for (Map.Entry<String, APIError> t : errors.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!groups.isEmpty() || !procs.isEmpty() || !funcs.isEmpty() || !types.isEmpty() || !errors.isEmpty()) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toStringInlineInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toStringInlineInfo());
        if (!groups.isEmpty()) {
            sb.append(", groups=" + groups.size());
        }
        if (!procs.isEmpty()) {
            sb.append(", procs=" + procs.size());
        }
        if (!funcs.isEmpty()) {
            sb.append(", funcs=" + funcs.size());
        }
        if (!types.isEmpty()) {
            sb.append(", types=" + types.size());
        }
        if (!errors.isEmpty()) {
            sb.append(", errors=" + errors.size());
        }
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        if (!groups.isEmpty()) {
            sb.append("\n  Groups[" + groups.size() + "]:");
            for (Map.Entry<String, APIGroup> t : groups.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        if (!procs.isEmpty()) {
            sb.append("\n  APIProcedures[" + procs.size() + "]");
            for (Map.Entry<String, APIProcedure[]> t : procs.entrySet()) {
                sb.append("\n    " + t.getKey() + "(" + t.getValue().length + "): ");
                if (t.getValue().length == 1) {
                    sb.append(t.getValue()[0].toFQNString().replace("\n", "\n      "));
                } else {
                    for (APIItem f : t.getValue()) {
                        sb.append("\n      " + f.toFQNString().replace("\n", "\n      "));
                    }
                }
            }
        }
        if (!funcs.isEmpty()) {
            sb.append("\n  Functions[" + funcs.size() + "]");
            for (Map.Entry<String, APIFunction[]> t : funcs.entrySet()) {
                sb.append("\n    " + t.getKey() + "(" + t.getValue().length + "): ");
                if (t.getValue().length == 1) {
                    sb.append(t.getValue()[0].toFQNString().replace("\n", "\n      "));
                } else {
                    for (APIItem f : t.getValue()) {
                        sb.append("\n      " + f.toFQNString().replace("\n", "\n      "));
                    }
                }
            }
        }
        if (!types.isEmpty()) {
            sb.append("\n  Types[" + types.size() + "]");
            for (Map.Entry<String, APIDataType> t : types.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        if (!errors.isEmpty()) {
            sb.append("\n  Errors[" + errors.size() + "]");
            for (Map.Entry<String, APIError> t : errors.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return groups.isEmpty()
                && procs.isEmpty()
                && funcs.isEmpty()
                && types.isEmpty()
                && errors.isEmpty()
                ;
    }
}
