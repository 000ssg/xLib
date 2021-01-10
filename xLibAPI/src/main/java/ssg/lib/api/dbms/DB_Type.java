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

import ssg.lib.api.*;
import java.sql.Types;

/**
 *
 * @author 000ssg
 */
public class DB_Type extends APIDataType {
    
    private static final long serialVersionUID = 1L;
    public Integer len;
    public Integer scale;
    public Integer prec;
    public String cs;
    public boolean mandatory;

    public DB_Type(APIItemCategory category, String name, String... scope) {
        super(category, name, scope);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.delete(sb.length() - 1, sb.length());
        sb.append(", len=" + len + ", scale=" + scale + ", prec=" + prec + ", cs=" + cs + ", mandatory=" + mandatory);
        sb.append('}');
        return sb.toString();
    }

    //        @Override
    //        public String toFQNString() {
    //            StringBuilder sb = new StringBuilder();
    //            sb.append(super.toFQNString());
    //            if (len != null) {
    //                sb.append("[" + len + "]");
    //            } else if (prec != null) {
    //                sb.append("[");
    //                sb.append(prec);
    //                if (scale != null) {
    //                    sb.append(",");
    //                    sb.append(scale);
    //                }
    //                sb.append("]");
    //            } else if (scale != null) {
    //                sb.append("[");
    //                sb.append(",");
    //                sb.append(scale);
    //                sb.append("]");
    //            }
    //            return sb.toString();
    //        }
    public int getSQLType() {
        return Types.OTHER;
    }

    public Class getJavaType() {
        return Object.class;
    }
}
