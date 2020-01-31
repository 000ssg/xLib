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
import ssg.lib.api.util.APISearchable;
import ssg.lib.api.util.APITools;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIItemCategory;

/**
 *
 * @author 000ssg
 */
public class DB_Package extends APIItem implements APISearchable {

    private static final long serialVersionUID = 1L;
    public Map<String, APIItem> content = new LinkedHashMap<>();
    public String version;

    public DB_Package(String name, String... scope) {
        super(APIItemCategory.sub_group, name, scope);
    }

    @Override
    public <T extends APIItem> Collection<T> find(APIMatcher matcher, Class type, Collection<T> result) {
        return APITools.find(matcher, type, result, content);
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);
        for (Collection coll : new Collection[]{content.values()}) {
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
        if (version != null) {
            sb.append(", version: '" + version.replace("\n", "\\n") + "'");
        }
        sb.append(", content=" + content.size());
        if (!content.isEmpty()) {
            sb.append("\n  Content:");
            for (Map.Entry<String, APIItem> t : content.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toString().replace("\n", "\n      "));
            }
        }
        if (!content.isEmpty()) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        if (version != null) {
            sb.append("\n  Version: " + version.replace("\n", "\n    "));
        }
        if (!content.isEmpty()) {
            sb.append("\n  Content[" + content.size() + "]");
            for (Map.Entry<String, APIItem> t : content.entrySet()) {
                sb.append("\n    " + t.getKey() + ": " + t.getValue().toFQNString().replace("\n", "\n      "));
            }
        }
        return sb.toString();
    }

}
