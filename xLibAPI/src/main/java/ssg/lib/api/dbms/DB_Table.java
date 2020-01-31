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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIItemCategory;

/**
 *
 * @author sesidoro
 */
public class DB_Table extends APIItem {

    private static final long serialVersionUID = 1L;
    String tablespace;
    public Map<String, DB_Col> columns = new LinkedHashMap<>();

    public DB_Table(String name, String... scope) {
        super(APIItemCategory.table, name, scope);
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);

        for (Collection coll : new Collection[]{columns.values()}) {
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
        sb.append(", tablespace=" + tablespace + ", columns=" + columns.size());
        if (!columns.isEmpty()) {
            sb.append("\n  Columns:");
            for (Map.Entry<String, DB_Col> t : columns.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!columns.isEmpty()) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        if (!columns.isEmpty()) {
            sb.append("\n  Columns[" + columns.size() + "]");
            for (Map.Entry<String, DB_Col> t : columns.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        return sb.toString();
    }

}
