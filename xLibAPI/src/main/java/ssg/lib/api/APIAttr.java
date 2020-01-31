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

import ssg.lib.api.dbms.DB_SQLType;

/**
 *
 * @author sesidoro
 */
public class APIAttr extends APIItem {

    private static final long serialVersionUID = 1L;
    public Integer order; // for ordered access...
    public APIDataType type;

    public APIAttr(String name) {
        super(APIItemCategory.attr, name);
    }

    public APIAttr(String name, APIDataType type) {
        super(APIItemCategory.attr, name);
        this.type = type;
    }

    @Override
    public void fixUsedIn(APIItem parent) {
        super.fixUsedIn(parent);
        if (type != null) {
            type.fixUsedIn(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.delete(sb.length() - 1, sb.length());
        sb.append(", order=" + order);
        sb.append(toStringInlineAdditions());
        sb.append(", type=");
        String ts = (type != null) ? (type instanceof DB_SQLType) ? type.toString() : type.fqn() : "";
        if (ts.contains("\n")) {
            sb.append("\n  " + ts.replace("\n", "\n  "));
            sb.append('\n');
        } else {
            sb.append(ts);
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toFQNString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toFQNString());
        sb.append(":" + type.fqn().replace("\n", "\n  "));
        return sb.toString();
    }

    public String toStringInlineAdditions() {
        return "";
    }

}
