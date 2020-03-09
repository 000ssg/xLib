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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * API item represent minimal API construction element and provides base set of
 * properties: category (like type, group, procedure), scope (identifying
 * context, used to evaluate fully qualified name, if applicable), item name
 * (unique within scope), access restrictions (optional). Items location within
 * hierarchy is marked via "usedIn" collection to be filled using "fixUsedIn"
 * methodI.
 *
 * @author 000ssg
 */
public abstract class APIItem implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;
    public APIItemCategory category;
    public String[] scope; // [schema, ?catalog]
    public String name;
    public APIAccess access;
    public APIItem prototype; // optional prototype, e.g. refers to item used as basis for creating this one...
    transient public Collection<APIItem> usedIn = new LinkedHashSet<>();

    public APIItem(APIItemCategory category, String name, String... scope) {
        this.category = category;
        this.name = name;
        if (scope != null && scope.length > 0) {
            int off = 0;
            for (int i = 0; i < scope.length; i++) {
                if (scope[i] != null) {
                    off = i + 1;
                }
            }
            if (off < scope.length) {
                scope = Arrays.copyOf(scope, off);
            }
        }
        this.scope = scope;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append('{');
        sb.append(toStringInlineInfo());
        if (access != null) {
            if (access.hasACL()) {
                sb.append("\n  access=" + access.toString().replace("\n", "\n  "));
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 1st line of toString output additional texts
     *
     * @return
     */
    public String toStringInlineInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("category=");
        sb.append(category);
        sb.append(", scope=");
        if (scope != null) {
            sb.append('[');
            for (int i = 0; i < scope.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(scope[i]);
            }
            sb.append(']');
        }
        sb.append(", name=");
        sb.append(name);
        if (usedIn != null) {
            sb.append(", usedIn=");
            sb.append(usedIn.size());
        }
        if (prototype != null) {
            sb.append(", prototype=");
            sb.append(prototype.fqn());
        }
        if (access != null) {
            if (access.hasACL()) {
            } else {
                sb.append(", access=" + access.toMask(access.get(null)));
            }
        }
        return sb.toString();
    }

    public String toFQNString() {
        return getClass().getSimpleName() + ":" + fqn();
    }

    public String fqn() {
        StringBuilder sb = new StringBuilder();
        if (scope != null) {
            for (String s : scope) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(s);
            }
        }
        if (sb.length() > 0) {
            sb.append('.');
        }
        sb.append(name);
        return sb.toString();
    }

    public void fixUsedIn(APIItem parent) {
        if (usedIn == null) {
            usedIn = new LinkedHashSet<>();
        }
        if (parent != null && !usedIn.contains(parent)) {
            usedIn.add(parent);
        }
    }

    public String[] getScopeForChild() {
        if(scope==null || scope.length==0) return new String[]{name};
        String[] sc=Arrays.copyOf(scope, scope.length+1);
        sc[sc.length-1]=name;
        return sc;
    }
    
    @Override
    public APIItem clone() throws CloneNotSupportedException {
        APIItem copy = (APIItem) super.clone();
        if (usedIn != null) {
            usedIn.clear();
        }
        fixUsedIn(null);
        return copy;
    }

}
