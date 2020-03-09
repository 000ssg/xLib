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
package ssg.lib.api.dbms;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.api.APIGroup;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIProcedure;

/**
 *
 * @author 000ssg
 */
public class DB_Schema extends APIGroup {

    private static final long serialVersionUID = 1L;
    public Map<String, DB_Table> tables = new LinkedHashMap<>();
    public Map<String, DB_Package> packages = new LinkedHashMap<>();

    public DB_Schema(String name, String... scope) {
        super(name, scope);
    }

    @Override
    public Map[] getItemCollections(Class type) {
        Map[] r = super.getItemCollections(type);

        r = Arrays.copyOf(r, r.length + 2);
        r[r.length - 2] = (type == null || DB_Package.class.isAssignableFrom(type) || APIProcedure.class.isAssignableFrom(type)) ? packages : null;
        r[r.length - 1] = (type == null || DB_Table.class.isAssignableFrom(type)) ? tables : null;
        return r;
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);

        for (Collection coll : new Collection[]{packages.values(), tables.values()}) {
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
        if (!packages.isEmpty()) {
            sb.append("\n  Packages: " + packages.keySet());
            for (Map.Entry<String, DB_Package> t : packages.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!tables.isEmpty()) {
            sb.append("\n  Tables: " + tables.keySet());
            for (Map.Entry<String, DB_Table> t : tables.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!tables.isEmpty() || !packages.isEmpty()) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toStringInlineInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toStringInlineInfo());

        if (!tables.isEmpty()) {
            sb.append(", tables=" + tables.size());
        }
        if (!packages.isEmpty()) {
            sb.append(", packages=" + packages.size());
        }
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        if (!packages.isEmpty()) {
            sb.append("\n  Packages[" + packages.size() + "]");
            for (Map.Entry<String, DB_Package> t : packages.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        if (!tables.isEmpty()) {
            sb.append("\n  Tables[" + tables.size() + "]");
            for (Map.Entry<String, DB_Table> t : tables.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
//        if (!procs.isEmpty()) {
//            sb.append("\n  APIProcedures[" + procs.size() + "]");
//            for (Map.Entry<String, APIAPIProcedure> t : procs.entrySet()) {
//                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
//            }
//        }
//        if (!funcs.isEmpty()) {
//            sb.append("\n  Functions[" + funcs.size() + "]");
//            for (Map.Entry<String, APIFunction> t : funcs.entrySet()) {
//                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
//            }
//        }
//        if (!types.isEmpty()) {
//            sb.append("\n  Types[" + types.size() + "]");
//            for (Map.Entry<String, APIType> t : types.entrySet()) {
//                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
//            }
//        }
        return sb.toString();
    }

}
