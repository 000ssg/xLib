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

import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.api.APIAttr;
import ssg.lib.api.APIDataType.APIObjectType;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.APIItemCategory;
import ssg.lib.api.APIItem;

/**
 *
 * @author 000ssg
 */
public class DB_ObjectType extends DB_Type implements DB_DataType, APIObjectType {

    private static final long serialVersionUID = 1L;
    public Map<String, APIAttr> attrs = new LinkedHashMap<>();
    public Map<String, APIProcedure> methods = new LinkedHashMap<>();

    public DB_ObjectType(String name, String... scope) {
        super(APIItemCategory.object, name, scope);
    }

    @Override
    public int getSQLType() {
        return Types.STRUCT;
    }

    @Override
    public Class getJavaType() {
        return Map.class;
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);
        for (Collection coll : new Collection[]{attrs.values(), methods.values()}) {
            if (coll != null) {
                for (Object obj : coll) {
                    if (obj == null) {
                        continue;
                    }
                    APIItem[] items = (obj instanceof APIItem) ? new APIItem[]{(APIItem) obj} : (APIItem[]) obj;
                    for (APIItem item : items) {
                        if (item != null) {
                            if(item.usedIn!=null && item.usedIn.contains(this)) continue;
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
        sb.append(", attrs=");
        sb.append(attrs.size());
        sb.append(", methods=");
        sb.append(methods.size());
        if (!attrs.isEmpty()) {
            sb.append("\n  Attributes:");
            for (Map.Entry<String, APIAttr> t : attrs.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!methods.isEmpty()) {
            sb.append("\n  Methods:");
            for (Map.Entry<String, APIProcedure> t : methods.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!attrs.isEmpty() || !methods.isEmpty()) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        if (!attrs.isEmpty()) {
            sb.append("\n  Attributes[" + attrs.size() + "]");
            for (Map.Entry<String, APIAttr> t : attrs.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        if (!methods.isEmpty()) {
            sb.append("\n  Methods[" + methods.size() + "]");
            for (Map.Entry<String, APIProcedure> t : methods.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        return sb.toString();
    }

    @Override
    public Map<String, APIAttr> attributes() {
        return attrs;
    }
    
}

