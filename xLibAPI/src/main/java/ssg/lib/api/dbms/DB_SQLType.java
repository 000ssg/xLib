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
import ssg.lib.api.APIItemCategory;

/**
 *
 * @author 000ssg
 */
public class DB_SQLType extends DB_Type {

    private static final long serialVersionUID = 1L;

    public DB_SQLType(APIItemCategory category, String name, String... scope) {
        super(category, name, scope);
    }

    public String fqn() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (len != null && len > 0) {
            sb.append('[');
            sb.append(len);
            if (cs != null && !cs.isEmpty()) {
                sb.append(", ");
                sb.append(cs);
            }
            sb.append(']');
        } else if (prec != null && prec != 0) {
            sb.append('[');
            sb.append(prec);
            if (scale != null && scale != 0) {
                sb.append(", ");
                sb.append(scale);
            }
            sb.append(']');
        } else if (scale != null && scale != 0) {
            sb.append('[');
            sb.append(", ");
            sb.append(scale);
            sb.append(']');
        } else if (cs != null && !cs.isEmpty()) {
            sb.append('[');
            sb.append(cs);
            sb.append(']');
        }
        sb.append('#');
        sb.append(getSQLType());
        return sb.toString();
    }

    @Override
    public int getSQLType() {
        if (name.startsWith("VARCHAR")) {
            return Types.VARCHAR;
        } else if (name.startsWith("NUMBER")) {
            return Types.DECIMAL;
        } else if (name.startsWith("FLOAT")) {
            return Types.FLOAT;
        } else if (name.startsWith("REAL")) {
            return Types.REAL;
        } else if (name.startsWith("DATE")) {
            return Types.DATE;
        } else {
            return Types.OTHER; // super.getSQLType();
        }
    }

    @Override
    public Class getJavaType() {
        if (name.startsWith("VARCHAR")) {
            return String.class;
        } else if (name.startsWith("NUMBER")) {
            return Number.class;
        } else if (name.startsWith("FLOAT")) {
            return Number.class;
        } else if (name.startsWith("REAL")) {
            return Number.class;
        } else if (name.startsWith("DATE")) {
            return java.util.Date.class;
        } else {
            return super.getJavaType();
        }
    }

    public static class DB_SQLTypeX extends DB_SQLType {

        int sqlType;

        public DB_SQLTypeX(APIItemCategory category, String name, int sqlType, String... scope) {
            super(category, name, scope);
            this.sqlType = sqlType;
        }

        @Override
        public Class getJavaType() {
            switch (getSQLType()) {
                case Types.VARCHAR:
                    return String.class;
                case Types.INTEGER:
                case Types.DECIMAL:
                case Types.FLOAT:
                case Types.REAL:
                    return Number.class;
                case Types.DATE:
                    return java.util.Date.class;
                case Types.ARRAY:
                case Types.STRUCT:
                case Types.OTHER:
                    return super.getJavaType();
            }
            return super.getJavaType();
        }

        @Override
        public int getSQLType() {
            return sqlType;
        }

    }
}
