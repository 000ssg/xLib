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
package ssg.lib.api.util;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author 000ssg
 */
public class JDBCTools {

    public static String string2json(Object text) {
        if (text != null) {
            return "\""
                    + ("" + text)
                            .replace("\"", "\\\"")
                            .replace("/", "_")
                            .replace("\\", "_")
                    + "\"";
        } else {
            return "null";
        }
    }

    /**
     *
     * @param rs
     * @param includeTypesInfo
     * @return
     * @throws SQLException
     */
    public static String dumpRS(ResultSet rs, String separator, boolean includeTypesInfo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        if (rs == null) {
            return sb.toString();
        }
        ResultSetMetaData meta = rs.getMetaData();

        try {

            if (separator == null || separator.isEmpty()) {
                separator = "\t|";
            }

            sb.append("NAME");
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                sb.append(separator + meta.getColumnName(i));
            }
            sb.append("\n");
            if (includeTypesInfo) {
                sb.append("TYPE");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    sb.append(separator + meta.getColumnType(i));
                }
                sb.append("\n");
                sb.append("CLASS");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String cn = meta.getColumnClassName(i);
                    if (cn.contains(".")) {
                        cn = cn.substring(cn.lastIndexOf(".") + 1);
                    }
                    sb.append("\t|" + cn);
                }
                sb.append("\n");
            }

            if (1 == 0 && rs.isAfterLast()) {
                sb.append("NO DATA");
            } else {
                int row = 0;
                while (rs.next()) {
                    sb.append("[" + row++ + "]");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        switch (meta.getColumnType(i)) {
                            case Types.BIT:
                                sb.append(separator + rs.getBoolean(i));
                                break;
                            case Types.BIGINT:
                                sb.append(separator + rs.getBigDecimal(i));
                                break;
                            case Types.DECIMAL:
                                sb.append(separator + rs.getBigDecimal(i));
                                break;
                            case Types.DOUBLE:
                                sb.append(separator + rs.getDouble(i));
                                break;
                            case Types.FLOAT:
                                sb.append(separator + rs.getFloat(i));
                                break;
                            case Types.INTEGER:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.NUMERIC:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.REAL:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.SMALLINT:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.TINYINT:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.BLOB:
                            case Types.VARBINARY:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.CLOB:
                            case Types.LONGNVARCHAR:
                            case Types.LONGVARBINARY:
                            case Types.LONGVARCHAR:
                            case Types.NCHAR:
                            case Types.NCLOB:
                            case Types.NVARCHAR:
                            case Types.SQLXML:
                            case Types.VARCHAR:
                                sb.append(separator + rs.getString(i));
                                break;
                            case Types.DATE:
                                sb.append(separator + rs.getDate(i));
                                break;
                            case Types.TIME:
                                sb.append(separator + rs.getTime(i));
                                break;
                            case Types.TIMESTAMP:
                            case Types.TIMESTAMP_WITH_TIMEZONE:
                            case Types.TIME_WITH_TIMEZONE:
                                sb.append(separator + rs.getTimestamp(i));
                                break;
                            default:
                                sb.append(separator + rs.getString(i));
                        }
                    }
                    sb.append("\n");
                }
            }
        } catch (Throwable th) {
            sb.append(th);
            th.printStackTrace();
        }
        return sb.toString();
    }

    public static String dumpRSList(List<Object[]> list, boolean hasMeta, String separator, boolean includeTypesInfo, String... colsOnly) throws SQLException {
        int[] icolsOnly = null;
        if (hasMeta && colsOnly != null) {
            //
            icolsOnly = new int[colsOnly.length];
            int off = 0;
            String[] names = (String[]) list.get(0);
            for (int i = 0; i < colsOnly.length; i++) {
                String cn = colsOnly[i];
                if (cn == null) {
                    continue;
                }
                for (int j = 0; j < names.length; j++) {
                    if (cn.equalsIgnoreCase(names[j])) {
                        icolsOnly[off++] = j;
                        break;
                    }
                }
            }
            if (off < icolsOnly.length) {
                icolsOnly = Arrays.copyOf(icolsOnly, off);
            }
        }
        return dumpRSList(list, hasMeta, separator, includeTypesInfo, icolsOnly);
    }

    /**
     *
     * @param rs
     * @param includeTypesInfo
     * @return
     * @throws SQLException
     */
    public static String dumpRSList(List<Object[]> list, boolean hasMeta, String separator, boolean includeTypesInfo, int... colsOnly) throws SQLException {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.isEmpty()) {
            return sb.toString();
        }

        String[] names = (hasMeta) ? (String[]) list.get(0) : null;
        Integer[] types = (hasMeta) ? (Integer[]) list.get(1) : null;
        String[] jtypes = (hasMeta) ? (String[]) list.get(2) : null;
        int off = (hasMeta) ? 3 : 0;
        int cols = (names != null) ? names.length : list.get(0).length;

        if (colsOnly == null || colsOnly.length == 0) {
            colsOnly = new int[cols];
            for (int i = 0; i < cols; i++) {
                colsOnly[i] = i;
            }
        }

        try {

            if (separator == null || separator.isEmpty()) {
                separator = "\t|";
            }

            if (hasMeta) {
                sb.append("NAME");
                for (int i : colsOnly) {
                    sb.append(separator + names[i]);
                }
                sb.append("\n");
                if (includeTypesInfo) {
                    sb.append("TYPE");
                    for (int i : colsOnly) {
                        sb.append(separator + types[i]);
                    }
                    sb.append("\n");
                    sb.append("CLASS");
                    for (int i : colsOnly) {
                        String cn = jtypes[i];
                        if (cn.contains(".")) {
                            cn = cn.substring(cn.lastIndexOf(".") + 1);
                        }
                        sb.append("\t|" + cn);
                    }
                    sb.append("\n");
                }
            }

            if (off == list.size()) {
                sb.append("NO DATA");
            } else {
                for (int row = off; row < list.size(); row++) {
                    sb.append("[" + (row - off) + ((row - off) < 10 ? " " : "") + "]");
                    for (int i : colsOnly) {
                        Object[] oo = list.get(row);
                        sb.append(separator + oo[i]);
                    }
                    sb.append("\n");
                }
            }
        } catch (Throwable th) {
            sb.append(th);
            th.printStackTrace();
        }
        return sb.toString();
    }

    /**
     *
     * @param rs
     * @param includeTypesInfo
     * @return
     * @throws SQLException
     */
    public static String rs2html(ResultSet rs, boolean includeTypesInfo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        if (rs == null) {
            return sb.toString();
        }
        ResultSetMetaData meta = rs.getMetaData();

        try {
            sb.append("<table border='1' class='rs'>");

            sb.append("\n  <tr class='rs'>");
            sb.append("<td class='rs'>");
            sb.append("NAME");
            sb.append("</td>");
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                sb.append("<td class='rs'>");
                sb.append(meta.getColumnName(i));
                sb.append("</td>");
            }
            sb.append("</tr>");
            if (includeTypesInfo) {
                sb.append("\n  <tr class='rs'>");
                sb.append("<td class='rs'>");
                sb.append("TYPE");
                sb.append("</td>");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    sb.append("<td class='rs'>");
                    sb.append(meta.getColumnType(i));
                    sb.append("</td>");
                }
                sb.append("</tr>");
                sb.append("\n  <tr class='rs'>");
                sb.append("<td class='rs'>");
                sb.append("CLASS");
                sb.append("</td>");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String cn = meta.getColumnClassName(i);
                    if (cn.contains(".")) {
                        cn = cn.substring(cn.lastIndexOf(".") + 1);
                    }
                    sb.append("<td class='rs'>");
                    sb.append(cn);
                    sb.append("</td>");
                }
                sb.append("</tr>");
            }

            if (1 == 0 && rs.isAfterLast()) {
                sb.append("\n  <tr class='rs'>");
                sb.append("<td class='rs'>");
                sb.append("NO DATA");
                sb.append("</td>");
                sb.append("</tr>");
            } else {
                int row = 0;
                while (rs.next()) {
                    sb.append("\n  <tr class='rs'>");
                    sb.append("<td class='rs'>");
                    sb.append(row++);
                    sb.append("</td>");
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        sb.append("<td class='rs'>");
                        switch (meta.getColumnType(i)) {
                            case Types.BIT:
                                sb.append(rs.getBoolean(i));
                                break;
                            case Types.BIGINT:
                                sb.append(rs.getBigDecimal(i));
                                break;
                            case Types.DECIMAL:
                                sb.append(rs.getBigDecimal(i));
                                break;
                            case Types.DOUBLE:
                                sb.append(rs.getDouble(i));
                                break;
                            case Types.FLOAT:
                                sb.append(rs.getFloat(i));
                                break;
                            case Types.INTEGER:
                                sb.append(rs.getString(i));
                                break;
                            case Types.NUMERIC:
                                sb.append(rs.getString(i));
                                break;
                            case Types.REAL:
                                sb.append(rs.getString(i));
                                break;
                            case Types.SMALLINT:
                                sb.append(rs.getString(i));
                                break;
                            case Types.TINYINT:
                                sb.append(rs.getString(i));
                                break;
                            case Types.BLOB:
                            case Types.VARBINARY:
                                sb.append(rs.getString(i));
                                break;
                            case Types.CLOB:
                            case Types.LONGNVARCHAR:
                            case Types.LONGVARBINARY:
                            case Types.LONGVARCHAR:
                            case Types.NCHAR:
                            case Types.NCLOB:
                            case Types.NVARCHAR:
                            case Types.SQLXML:
                            case Types.VARCHAR:
                                sb.append(rs.getString(i));
                                break;
                            case Types.DATE:
                                sb.append(rs.getDate(i));
                                break;
                            case Types.TIME:
                                sb.append(rs.getTime(i));
                                break;
                            case Types.TIMESTAMP:
                            case Types.TIMESTAMP_WITH_TIMEZONE:
                            case Types.TIME_WITH_TIMEZONE:
                                sb.append(rs.getTimestamp(i));
                                break;
                            default:
                                sb.append(rs.getString(i));
                        }
                        sb.append("</td>");
                    }
                    sb.append("</tr>");
                }
            }
        } catch (Throwable th) {
            sb.append(th);
            th.printStackTrace();
        } finally {
            sb.append("\n</table>");
        }
        return sb.toString();
    }

    /**
     *
     * @param rs
     * @param includeTypesInfo
     * @return
     * @throws SQLException
     */
    public static String rs2json(ResultSet rs, boolean includeTypesInfo) throws SQLException {
        StringBuilder sb = new StringBuilder();
        if (rs == null) {
            return sb.toString();
        }
        ResultSetMetaData meta = rs.getMetaData();

        try {
            sb.append("{");
            int pos = sb.length();

            Collection[] ccc = new Collection[meta.getColumnCount() + 1];
            if (1 == 0 && rs.isAfterLast()) {
                //sb.append("NO DATA");
            } else {
                sb.append(",\n  data: [");
                int row = 0;
                while (rs.next()) {
                    if (row > 1) {
                        sb.append(",");
                    }
                    sb.append("\n    [");
                    sb.append(row++);
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        sb.append(", ");
                        int vpos = sb.length();
                        switch (meta.getColumnType(i)) {
                            case Types.BIT:
                                sb.append(rs.getBoolean(i));
                                break;
                            case Types.BIGINT:
                                sb.append(rs.getBigDecimal(i));
                                break;
                            case Types.DECIMAL:
                                sb.append(rs.getBigDecimal(i));
                                break;
                            case Types.DOUBLE:
                                sb.append(rs.getDouble(i));
                                break;
                            case Types.FLOAT:
                                sb.append(rs.getFloat(i));
                                break;
                            case Types.INTEGER:
                                sb.append(rs.getString(i));
                                break;
                            case Types.NUMERIC:
                                sb.append(rs.getString(i));
                                break;
                            case Types.REAL:
                                sb.append(rs.getString(i));
                                break;
                            case Types.SMALLINT:
                                sb.append(rs.getString(i));
                                break;
                            case Types.TINYINT:
                                sb.append(rs.getString(i));
                                break;
                            case Types.BLOB:
                            case Types.VARBINARY:
                                sb.append(string2json(rs.getString(i)));
                                break;
                            case Types.CLOB:
                            case Types.LONGNVARCHAR:
                            case Types.LONGVARBINARY:
                            case Types.LONGVARCHAR:
                            case Types.NCHAR:
                            case Types.NCLOB:
                            case Types.NVARCHAR:
                            case Types.SQLXML:
                            case Types.VARCHAR:
                                sb.append(string2json(rs.getString(i)));
                                break;
                            case Types.DATE:
                                sb.append(string2json(rs.getDate(i)));
                                break;
                            case Types.TIME:
                                sb.append(string2json(rs.getTime(i)));
                                break;
                            case Types.TIMESTAMP:
                            case Types.TIMESTAMP_WITH_TIMEZONE:
                            case Types.TIME_WITH_TIMEZONE:
                                sb.append(string2json(rs.getTimestamp(i)));
                                break;
                            default:
                                sb.append(string2json(rs.getString(i)));
                        }
                        String vs = sb.substring(vpos, sb.length());
                        if (ccc[i] == null) {
                            ccc[i] = new HashSet<>();
                        }
                        ccc[i].add(vs);
                        sb.append("");
                    }
                    sb.append("]");
                }
                sb.append("\n  ]");
            }

            StringBuilder sbm = new StringBuilder();
            sbm.append("\n  meta: [");
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (i > 1) {
                    sbm.append(",");
                }
                sbm.append("\n    {\n      name: \"");
                sbm.append(meta.getColumnName(i));
                sbm.append("\"");
                sbm.append(",\n      order: ");
                sbm.append(i);
                sbm.append(",\n      type: \"");
                sbm.append(meta.getColumnType(i));
                sbm.append("\"");
                sbm.append(",\n      class: \"");
                sbm.append(meta.getColumnClassName(i));
                sbm.append("\"");
                if (ccc[i] != null && !ccc[i].isEmpty()) {
                    String[] cs = (String[]) ccc[i].toArray(new String[ccc[i].size()]);
                    Arrays.sort(cs);
                    sbm.append(",\n      opts: [");
                    for (int j = 0; j < cs.length; j++) {
                        if (j > 0) {
                            sbm.append(",");
                        }
                        sbm.append(cs[j]);
                    }
                    sbm.append("]");
                }
                sbm.append("\n    }");
            }
            sbm.append("\n    ]");
            sb.insert(pos, sbm);

        } catch (Throwable th) {
            sb.append(th);
            th.printStackTrace();
        } finally {
            sb.append("}");
        }
        return sb.toString();
    }

    public static List<Object[]> rs2list(ResultSet rs, boolean includeTypesInfo) throws SQLException {
        return rs2list(rs, includeTypesInfo, true);
    }

    /**
     *
     * @param rs
     * @param includeTypesInfo
     * @return
     * @throws SQLException
     */
    public static List<Object[]> rs2list(ResultSet rs, boolean includeTypesInfo, boolean closeRS) throws SQLException {
        List<Object[]> r = new ArrayList<>();

        ResultSetMetaData meta = rs.getMetaData();

        try {
            String[] names = new String[meta.getColumnCount()];
            Integer[] types = new Integer[meta.getColumnCount()];
            String[] jtypes = new String[meta.getColumnCount()];
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                names[i - 1] = meta.getColumnName(i);
                types[i - 1] = meta.getColumnType(i);
                jtypes[i - 1] = meta.getColumnClassName(i);
            }
            r.add(names);
            if (includeTypesInfo) {
                r.add(types);
                r.add(jtypes);
            }

            //Collection[] ccc = new Collection[meta.getColumnCount() + 1];
            if (1 == 0 && rs.isAfterLast()) {
                //sb.append("NO DATA");
            } else {
                Object[] row = new Object[names.length];
                while (rs.next()) {
                    r.add(row);
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        switch (meta.getColumnType(i)) {
                            case Types.BIT:
                                row[i - 1] = (rs.getBoolean(i));
                                break;
                            case Types.BIGINT:
                                row[i - 1] = (rs.getBigDecimal(i));
                                break;
                            case Types.DECIMAL:
                                row[i - 1] = (rs.getBigDecimal(i));
                                break;
                            case Types.DOUBLE:
                                row[i - 1] = (rs.getDouble(i));
                                break;
                            case Types.FLOAT:
                                row[i - 1] = (rs.getFloat(i));
                                break;
                            case Types.INTEGER:
                                row[i - 1] = (rs.getString(i));
                                break;
                            case Types.NUMERIC:
                                row[i - 1] = (rs.getBigDecimal(i));
                                break;
                            case Types.REAL:
                                row[i - 1] = (rs.getDouble(i));
                                break;
                            case Types.SMALLINT:
                                row[i - 1] = (rs.getLong(i));
                                break;
                            case Types.TINYINT:
                                row[i - 1] = (rs.getInt(i));
                                break;
                            case Types.BLOB:
                            case Types.VARBINARY:
                                row[i - 1] = (rs.getString(i));
                                break;
                            case Types.CLOB:
                            case Types.LONGNVARCHAR:
                            case Types.LONGVARBINARY:
                            case Types.LONGVARCHAR:
                            case Types.NCHAR:
                            case Types.NCLOB:
                            case Types.NVARCHAR:
                            case Types.SQLXML:
                            case Types.VARCHAR:
                                row[i - 1] = (rs.getString(i));
                                break;
                            case Types.DATE:
                                row[i - 1] = (rs.getDate(i));
                                break;
                            case Types.TIME:
                                row[i - 1] = (rs.getTime(i));
                                break;
                            case Types.TIMESTAMP:
                            case Types.TIMESTAMP_WITH_TIMEZONE:
                            case Types.TIME_WITH_TIMEZONE:
                                row[i - 1] = (rs.getTimestamp(i));
                                break;
                            default:
                                row[i - 1] = (rs.getString(i));
                        }
                    }
                    row = new Object[names.length];
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            if (rs != null && closeRS) {
                try {
                    rs.close();
                } catch (SQLException sex) {
                }
            }
        }
        return r;
    }
}
