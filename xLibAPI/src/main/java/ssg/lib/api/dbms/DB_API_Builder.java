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

import ssg.lib.api.util.JDBCTools;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.api.APIAttr;
import ssg.lib.api.APIFunction;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.APIDataType;
import ssg.lib.api.APIParameterDirection;
import ssg.lib.api.APIItemCategory;

/**
 *
 * @author 000ssg
 */
public class DB_API_Builder {

    public static DB_API scanJDBC(DB_API dbm, Connection conn, String[] schemas, DBItemVerifier verifier) throws SQLException {
        try {
            Map<String, APIItem> resolved = new HashMap<>();
            Map<String, APIItem> unresolved = new HashMap<>();

            DatabaseMetaData md = conn.getMetaData();

            return dbm;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    public static DB_API scanOracle(DB_API dbm, Connection conn, String[] schemas, DBItemVerifier verifier) throws SQLException {
        try {
            Map<String, APIItem> resolved = new HashMap<>();
            Map<String, APIItem> unresolved = new HashMap<>();

            for (String schema : schemas) {
                schema = schema.toUpperCase();
                String sqlTbl = "SELECT * FROM ALL_TABLES where owner='" + schema + "' order by owner";
                String sqlTblCols = "SELECT * FROM ALL_TAB_COLS where owner='" + schema + "' order by owner, table_name, column_id";
                //String sqlT = "SELECT * FROM ALL_TYPES where owner='" + schema + "' order by owner, type_name";
                String sqlT = "SELECT * FROM ALL_TYPES order by owner, type_name";
                String sqlCT = "SELECT * FROM ALL_COLL_TYPES where owner='" + schema + "' order by owner, type_name";
                //String sqlTA = "SELECT * FROM ALL_TYPE_ATTRS where owner='" + schema + "' order by owner, type_name, attr_no";
                String sqlTA = "SELECT * FROM ALL_TYPE_ATTRS order by owner, type_name, attr_no";

                String sqlPR = "SELECT * FROM ALL_PROCEDURES where owner='" + schema + "' order by owner";
                String sqlPRARG = "SELECT * FROM ALL_ARGUMENTS where owner='" + schema + "' order by owner";

                // tables
                List<Object[]> ts = select(conn, sqlTbl);
                List<Object[]> tcs = select(conn, sqlTblCols);

                // types
                List<Object[]> sts = select(conn, sqlT);
                List<Object[]> scs = select(conn, sqlCT);
                List<Object[]> sas = select(conn, sqlTA);

                // procedures
                List<Object[]> prs = select(conn, sqlPR);
                List<Object[]> pras = select(conn, sqlPRARG);

                DB_Schema dbs = new DB_Schema(schema);
                dbm.groups.put(schema, dbs);
                dbs.usedIn.add(dbm);

                if (verifier != null) {
                    verifier.onItem(conn, dbs, null);
                }

                // table names
                int tsTON = indexOf(ts, "OWNER");
                int tsTN = indexOf(ts, "TABLE_NAME");
                int tsTSN = indexOf(ts, "TABLESPACE_NAME");

                // table column names
                int tcsCN = indexOf(tcs, "COLUMN_NAME");
                int tcsDTM = indexOf(tcs, "DATA_TYPE_MOD");
                int tcsDTO = indexOf(tcs, "DATA_TYPE_OWNER");
                int tcsDTN = indexOf(tcs, "DATA_TYPE");
                int tcsLEN = indexOf(tcs, "DATA_LENGTH");
                int tcsPRE = indexOf(tcs, "DATA_PRECISION");
                int tcsSC = indexOf(tcs, "DATA_SCALE");
                int tcsCS = indexOf(tcs, "CHARACTER_SET_NAME");
                int tcsNL = indexOf(tcs, "NULLABLE");
                int tcsOrder = indexOf(tcs, "COLUMN_ID");

                // type names
                int stsOW = indexOf(sts, "OWNER");
                int stsTN = indexOf(sts, "TYPE_NAME");
                int stsTC = indexOf(sts, "TYPECODE");
                int stsAs = indexOf(sts, "ATTRIBUTES");
                int stsMs = indexOf(sts, "METHODS");

                // collection types... (if stsTC = COLLECTION
                int scsTN = indexOf(scs, "TYPE_NAME");
                int scsCT = indexOf(scs, "COLL_TYPE");
                int scsETO = indexOf(scs, "ELEM_TYPE_OWNER");
                int scsETN = indexOf(scs, "ELEM_TYPE_NAME");
                int scsLEN = indexOf(scs, "LENGTH");
                int scsPRE = indexOf(scs, "PRECISION");
                int scsSC = indexOf(scs, "SCALE");
                int scsCS = indexOf(scs, "CHARACTER_SET_NAME");
                int scsNL = indexOf(scs, "NULL_STORED");

                // attribbutes, if obj has stsAs > 0
                int sasTN = indexOf(sas, "TYPE_NAME");
                int sasAN = indexOf(sas, "ATTR_NAME");
                int sasATM = indexOf(sas, "ATTR_TYPE_MOD");
                int sasATO = indexOf(sas, "ATTR_TYPE_OWNER");
                int sasATN = indexOf(sas, "ATTR_TYPE_NAME");
                int sasLEN = indexOf(sas, "LENGTH");
                int sasPRE = indexOf(sas, "PRECISION");
                int sasSC = indexOf(sas, "SCALE");
                int sasCS = indexOf(sas, "CHARACTER_SET_NAME");
                //int sasNL = indexOf(sas, "NULL_STORED");
                int sasOrder = indexOf(sas, "ATTR_NO");

                // procedure names
                int prPO = indexOf(prs, "OWNER");
                int prON = indexOf(prs, "OBJECT_NAME");
                int prOT = indexOf(prs, "OBJECT_TYPE"); // TYPE/PACKAGE/FUNCTION ?
                int prPN = indexOf(prs, "PROCEDURE_NAME");

                // procedure args
                int praO = indexOf(pras, "OWNER");
                int praON = indexOf(pras, "OBJECT_NAME");
                int praPN = indexOf(pras, "PACKAGE_NAME");
                int praAN = indexOf(pras, "ARGUMENT_NAME");
                int praDT = indexOf(pras, "DATA_TYPE");
                int praDLevel = indexOf(pras, "DATA_LEVEL");
                int praDefaulted = indexOf(pras, "DEFAULTED");
                int praDefault = indexOf(pras, "DEFAULT_VALUE");
                int praDefaultLen = indexOf(pras, "DEFAULT_LENGTH");
                int praInOut = indexOf(pras, "IN_OUT"); // IN, OUT, IN/OUT
                int praLEN = indexOf(pras, "DATA_LENGTH");
                int praPRE = indexOf(pras, "DATA_PRECISION");
                int praSC = indexOf(pras, "DATA_SCALE");
                int praCS = indexOf(pras, "CHARACTER_SET_NAME");
                int praDTO = indexOf(pras, "TYPE_OWNER");
                int praDTN = indexOf(pras, "TYPE_NAME");
                int praPLS = indexOf(pras, "PLS_TYPE");
                int praCL = indexOf(pras, "CHAR_LENGTH");
                int praOrder = indexOf(pras, "POSITION");
                int praSeq = indexOf(pras, "SEQUENCE");

                // scan tables
                boolean first = true;
                for (Object[] oo : ts) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    String ton = (String) oo[tsTON];
                    String tn = (String) oo[tsTN];
                    String tsn = (String) oo[tsTSN];

                    DB_Table tbl = new DB_Table(tn, schema);
                    tbl.tablespace = tsn;
                    dbs.tables.put(tn, tbl);
                    tbl.usedIn.add(dbs);
                    if (verifier != null) {
                        verifier.onItem(conn, tbl, dbs);
                    }

                    int[] cr = rangeOf(tcs, "TABLE_NAME", tn);
                    if (cr[0] != -1) {
                        for (int i = cr[0]; i <= cr[1]; i++) {
                            Object[] co = tcs.get(i);
                            String cn = (String) co[tcsCN];
                            String dtm = (String) co[tcsDTM]; // ?
                            String dto = (String) co[tcsDTO];
                            String dtn = (String) co[tcsDTN];
                            if (dtn == null) {
                                continue;
                            }
                            Number cord = (Number) co[tcsOrder];
                            if (dto != null) {
                                // UDT!
                                DB_Col da = new DB_Col(cn);
                                if (cord != null) {
                                    da.order = cord.intValue();
                                }
                                tbl.columns.put(cn, da);
                                da.usedIn.add(tbl);
                                if (verifier != null) {
                                    verifier.onItem(conn, da, tbl);
                                }

                                APIDataType dt = new APIDataType(APIItemCategory.object, dtn, dto);
                                if (resolved.containsKey(dt.fqn())) {
                                    dt = (APIDataType) resolved.get(dt.fqn());
                                } else {
                                    unresolved.put(dt.fqn(), dt);
                                }
                                da.type = dt;
                                dt.usedIn.add(da);
                                if (verifier != null) {
                                    verifier.onItem(conn, dt, da);
                                }
                            } else {
                                DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, dtn);
                                Number atl = (Number) co[tcsLEN];
                                Number atp = (Number) co[tcsPRE];
                                Number ats = (Number) co[tcsSC];
                                String atcs = (String) co[tcsCS];
                                if (atl != null) {
                                    t.len = atl.intValue();
                                }
                                if (atp != null) {
                                    t.prec = atp.intValue();
                                }
                                if (ats != null) {
                                    t.scale = ats.intValue();
                                }
                                if (atcs != null) {
                                    t.cs = atcs;
                                }

                                if (dbm.types.containsKey(t.fqn())) {
                                    t = (DB_SQLType) dbm.types.get(t.fqn());
                                } else {
                                    dbm.types.put(t.fqn(), t);
                                }
                                DB_Col da = new DB_Col(cn, t);
                                if (cord != null) {
                                    da.order = cord.intValue();
                                }
                                t.usedIn.add(da);
                                if (verifier != null) {
                                    verifier.onItem(conn, t, da);
                                }
                                tbl.columns.put(cn, da);
                                da.usedIn.add(tbl);
                                if (verifier != null) {
                                    verifier.onItem(conn, da, tbl);
                                }
                            }

                        }
                    }
                }

                //
                ProceedRange<DB_ObjectType> proceedObjectAttrs = (dbo, min, max) -> {
                    for (int i = min; i <= max; i++) {
                        Object[] ao = sas.get(i);
                        String an = (String) ao[sasAN];
                        String ato = (String) ao[sasATO];
                        String atn = (String) ao[sasATN];
                        if (atn == null) {
                            continue;
                        }
                        Number atord = (Number) ao[sasOrder];
                        if (ato != null) {
                            // UDT!
                            APIAttr da = new APIAttr(an);
                            if (atord != null) {
                                da.order = atord.intValue();
                            }
                            dbo.attrs.put(an, da);
                            da.usedIn.add(dbo);
                            if (verifier != null) {
                                verifier.onItem(conn, da, dbo);
                            }

                            APIDataType dt = new APIDataType(APIItemCategory.object, atn, ato);
                            if (resolved.containsKey(dt.fqn())) {
                                dt = (APIDataType) resolved.get(dt.fqn());
                            } else {
                                unresolved.put(dt.fqn(), dt);
                            }
                            da.type = dt;
                            dt.usedIn.add(da);
                            if (verifier != null) {
                                verifier.onItem(conn, dt, da);
                            }
                        } else {
                            DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, atn);
                            Number atl = (Number) ao[sasLEN];
                            Number atp = (Number) ao[sasPRE];
                            Number ats = (Number) ao[sasSC];
                            String atcs = (String) ao[sasCS];
                            if (atl != null) {
                                t.len = atl.intValue();
                            }
                            if (atp != null) {
                                t.prec = atp.intValue();
                            }
                            if (ats != null) {
                                t.scale = ats.intValue();
                            }
                            if (atcs != null) {
                                t.cs = atcs;
                            }

                            if (dbm.types.containsKey(t.fqn())) {
                                t = (DB_SQLType) dbm.types.get(t.fqn());
                            } else {
                                dbm.types.put(t.fqn(), t);
                            }
                            APIAttr da = new APIAttr(an, t);
                            if (atord != null) {
                                da.order = atord.intValue();
                            }
                            t.usedIn.add(da);
                            if (verifier != null) {
                                verifier.onItem(conn, t, da);
                            }
                            dbo.attrs.put(an, da);
                            da.usedIn.add(dbo);
                            if (verifier != null) {
                                verifier.onItem(conn, da, dbo);
                            }
                        }
                    }

                };

                // scan object types -> attrs or coll type when detected...
                first = true;
                for (Object[] oo : sts) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    String tn = (String) oo[stsTN];
                    String to = (String) oo[stsOW];
                    String tc = (String) oo[stsTC];
                    Number tAs = (Number) oo[stsAs];
                    Number tMs = (Number) oo[stsMs];

                    if ("OBJECT".equalsIgnoreCase(tc)) {
                        DB_ObjectType dbo = new DB_ObjectType(tn, to);//schema);
                        resolved.put(dbo.fqn(), dbo);
                        if (schema.equals(to)) {
                            dbs.types.put(tn, dbo);
                        } else {
                            dbm.types.put(dbo.fqn(), dbo);
                        }
                        dbo.usedIn.add(dbs);
                        if (verifier != null) {
                            verifier.onItem(conn, dbo, dbs);
                        }
                        if (tAs.intValue() > 0) {
                            int[] ar = rangeOf(sas, new String[]{"OWNER", "TYPE_NAME"}, new String[]{to, tn});
                            if (ar[0] != -1) {
                                proceedObjectAttrs.handle(dbo, ar[0], ar[1]);
//                                for (int i = ar[0]; i <= ar[1]; i++) {
//                                    Object[] ao = sas.get(i);
//                                    String an = (String) ao[sasAN];
//                                    String ato = (String) ao[sasATO];
//                                    String atn = (String) ao[sasATN];
//                                    if (atn == null) {
//                                        continue;
//                                    }
//                                    Number atord = (Number) ao[sasOrder];
//                                    if (ato != null) {
//                                        // UDT!
//                                        Attr da = new Attr(an);
//                                        if (atord != null) {
//                                            da.order = atord.intValue();
//                                        }
//                                        dbo.attrs.put(an, da);
//                                        da.usedIn.add(dbo);
//                                        if (verifier != null) {
//                                            verifier.onItem(conn, da, dbo);
//                                        }
//
//                                        DataType dt = new DataType(CATEGORY.object, atn, ato);
//                                        if (resolved.containsKey(dt.fqn())) {
//                                            dt = (DataType) resolved.get(dt.fqn());
//                                        } else {
//                                            unresolved.put(dt.fqn(), dt);
//                                        }
//                                        da.type = dt;
//                                        dt.usedIn.add(da);
//                                        if (verifier != null) {
//                                            verifier.onItem(conn, dt, da);
//                                        }
//                                    } else {
//                                        DB_SQLType t = new DB_SQLType(CATEGORY.data_type, atn);
//                                        Number atl = (Number) ao[sasLEN];
//                                        Number atp = (Number) ao[sasPRE];
//                                        Number ats = (Number) ao[sasSC];
//                                        String atcs = (String) ao[sasCS];
//                                        if (atl != null) {
//                                            t.len = atl.intValue();
//                                        }
//                                        if (atp != null) {
//                                            t.prec = atp.intValue();
//                                        }
//                                        if (ats != null) {
//                                            t.scale = ats.intValue();
//                                        }
//                                        if (atcs != null) {
//                                            t.cs = atcs;
//                                        }
//
//                                        if (dbm.types.containsKey(t.fqn())) {
//                                            t = (DB_SQLType) dbm.types.get(t.fqn());
//                                        } else {
//                                            dbm.types.put(t.fqn(), t);
//                                        }
//                                        Attr da = new Attr(an, t);
//                                        if (atord != null) {
//                                            da.order = atord.intValue();
//                                        }
//                                        t.usedIn.add(da);
//                                        if (verifier != null) {
//                                            verifier.onItem(conn, t, da);
//                                        }
//                                        dbo.attrs.put(an, da);
//                                        da.usedIn.add(dbo);
//                                        if (verifier != null) {
//                                            verifier.onItem(conn, da, dbo);
//                                        }
//                                    }
//                                }
                            }
                        }
                    } else if ("COLLECTION".equalsIgnoreCase(tc)) {
                        DB_CollectionType dbo = new DB_CollectionType(tn, to);//schema);
                        if (unresolved.containsKey(dbo.fqn())) {
                            APIDataType uc = (APIDataType) unresolved.remove(dbo.fqn());
                            for (APIItem item : uc.usedIn) {
                                //
                                if (item instanceof APIAttr) {
                                    APIAttr p = (APIAttr) item;
                                    p.type = dbo;
                                    dbo.usedIn.add(p);
                                    int a = 0;
                                } else if (item instanceof DB_CollectionType) {
                                    DB_CollectionType p = (DB_CollectionType) item;
                                    p.itemType = dbo;
                                    dbo.usedIn.add(p);
                                    int a = 0;
                                } else {
                                    int a = 0;
                                }
                            }
                        }
                        resolved.put(dbo.fqn(), dbo);
                        dbs.types.put(tn, dbo);
                        if (verifier != null) {
                            verifier.onItem(conn, dbo, dbs);
                        }

                        int[] ar = rangeOf(scs, new String[]{"OWNER", "TYPE_NAME"}, new String[]{to, tn});
                        if (ar[0] != -1) {
                            APIDataType dt = null;
                            Object[] co = scs.get(ar[0]);
                            String ato = (String) co[scsETO];
                            String atn = (String) co[scsETN];
                            if (atn == null) {
                                continue;
                            }
                            if (ato != null) {
                                // UDT!
                                dt = new APIDataType(APIItemCategory.object, atn, ato);
                                if (resolved.containsKey(dt.fqn())) {
                                    dt = (APIDataType) resolved.get(dt.fqn());
                                } else {
                                    unresolved.put(dt.fqn(), dt);
                                }
                            } else {
                                DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, atn);
                                Number atl = (Number) co[scsLEN];
                                Number atp = (Number) co[scsPRE];
                                Number ats = (Number) co[scsSC];
                                String atcs = (String) co[scsCS];
                                if (atl != null) {
                                    t.len = atl.intValue();
                                }
                                if (atp != null) {
                                    t.prec = atp.intValue();
                                }
                                if (ats != null) {
                                    t.scale = ats.intValue();
                                }
                                if (atcs != null) {
                                    t.cs = atcs;
                                }

                                if (dbm.types.containsKey(t.fqn())) {
                                    t = (DB_SQLType) dbm.types.get(t.fqn());
                                } else {
                                    dbm.types.put(t.fqn(), t);
                                }
                                dt = t;
                            }

                            if (dt != null) {
                                dbo.itemType = dt;
                                dt.usedIn.add(dbo);
                                if (verifier != null) {
                                    verifier.onItem(conn, dt, dbo);
                                }
                            } else {
                                int a = 0;
                            }
                        }

                    } else {
                        int a = 0;
                    }
                }

                // check if can resolve...
                {
                    Collection<String> done = new HashSet<>();
                    done.add("");
                    while (!done.isEmpty()) {
                        done.clear();
                        for (String s : unresolved.keySet()) {
                            APIItem dt = unresolved.get(s);
                            int[] ar = rangeOf(sas, new String[]{"OWNER", "TYPE_NAME"}, new String[]{dt.scope[0], dt.name});
                            if (resolved.containsKey(s)) {
                                done.add(s);
                            }
                        }
                        if (!done.isEmpty()) {
                            for (String s : done) {
                                unresolved.remove(s);
                            }
                        }
                    }
                }

                // scan procedures
                first = true;
                for (Object[] oo : prs) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    String po = (String) oo[prPO];
                    String pn = (String) oo[prON];
                    String pt = (String) oo[prOT];
                    String prn = (String) oo[prPN];

                    // fix naming if function and no procedure name -> function name is object name...
                    if (prn == null && "FUNCTION".equals(pt)) {
                        prn = pn;
                        pn = null;
                    }

                    // skip if no function/procedure name...
                    if (prn == null) {
                        continue;
                    }

                    APIProcedure dbp = null;
                    boolean treatAsFunction = false;

                    // test if function (i.e. has unnamed OUT parameter 
                    {
                        int[] ar = rangeOf(pras, new String[]{"PACKAGE_NAME", "OBJECT_NAME"}, new Object[]{pn, prn});
                        if (ar[0] != -1) {
                            for (int i = ar[0]; i <= ar[1]; i++) {
                                Object[] ao = pras.get(i);
                                String an = (String) ao[praAN];
                                String aio = (String) ao[praInOut];
                                Number dataLevel = (Number) ao[praDLevel];
                                if (dataLevel.intValue() > 0) {
                                    continue;
                                }
                                if (an == null && "OUT".equalsIgnoreCase(aio)) {
                                    treatAsFunction = true;
                                    break;
                                }
                            }
                        }
                    }

                    if ("REVISION".equals(prn) && !treatAsFunction) {
                        int[] ar = rangeOf(pras, new String[]{"PACKAGE_NAME", "OBJECT_NAME"}, new Object[]{pn, prn});
                        if (ar[0] != -1) {
                            for (int i = ar[0]; i <= ar[1]; i++) {
                                Object[] ao = pras.get(i);
                                String an = (String) ao[praAN];
                                String aio = (String) ao[praInOut];
                                Number dataLevel = (Number) ao[praDLevel];
                                if (dataLevel.intValue() > 0) {
                                    continue;
                                }
                                if (an == null && "OUT".equalsIgnoreCase(aio)) {
                                    treatAsFunction = true;
                                    break;
                                }
                            }
                        }
                    }

                    if ("TYPE".equalsIgnoreCase(pt)) {
                        APIDataType t = new APIDataType(APIItemCategory.object, pn, po);
                        APIItem dbo = resolved.get(t.fqn());
                        dbp = (treatAsFunction) ? new APIFunction(prn, po, pn) : new APIProcedure(prn, po, pn);
                        if (dbo instanceof DB_ObjectType) {
                            ((DB_ObjectType) dbo).methods.put(prn, dbp);
                        } else if (dbo instanceof DB_CollectionType) {
                            ((DB_CollectionType) dbo).methods.put(prn, dbp);
                        }
                        //resolved.put(dbp.fqn(), dbp);
                        //dbs.procs.put(prn, dbp);
                        //dbp.usedIn.add(dbs);
                    } else if ("FUNCTION".equalsIgnoreCase(pt)) {
                        APIFunction dbf = new APIFunction(prn, po);
                        resolved.put(dbf.fqn(), dbf);
                        dbs.funcs.put(prn, new APIFunction[]{dbf});
                        dbf.usedIn.add(dbs);
                        if (verifier != null) {
                            verifier.onItem(conn, dbf, dbs);
                        }
                        dbp = dbf;
                    } else if ("PACKAGE".equalsIgnoreCase(pt)) {
                        if (prn == null) {
                            //continue;
                        }
                        DB_Package dbpkg = new DB_Package(pn, po);
                        if (pn != null) {
                            if (resolved.containsKey(dbpkg.fqn())) {
                                dbpkg = (DB_Package) resolved.get(dbpkg.fqn());
                            } else {
                                resolved.put(dbpkg.fqn(), dbpkg);
                                if (verifier != null) {
                                    verifier.onItem(conn, dbpkg, dbs);
                                }
                            }
                            dbs.packages.put(pn, dbpkg);
                            dbpkg.usedIn.add(dbs);
                        } else {
                            dbpkg = null;
                        }

                        if (dbpkg == null) {
                            dbp = (treatAsFunction) ? new APIFunction(prn, po) : new APIProcedure(prn, po);
                        } else {
                            dbp = (treatAsFunction) ? new APIFunction(prn, po, pn) : new APIProcedure(prn, po, pn);
                        }
                        resolved.put(dbp.fqn(), dbp);

                        if (dbpkg == null) {
                            dbs.procs.put(prn, new APIProcedure[]{dbp});
                            dbp.usedIn.add(dbs);
                            if (verifier != null) {
                                verifier.onItem(conn, dbp, dbs);
                            }
                        } else {
                            dbpkg.content.put(prn, dbp);
                            dbp.usedIn.add(dbpkg);
                            if (verifier != null) {
                                verifier.onItem(conn, dbp, dbpkg);
                            }
                        }
                        int a = 0;
                    }

//            // procedure names
//            int prPO = indexOf(prs, "OWNER");
//            int prON = indexOf(prs, "OBJECT_NAME");
//            int prOT = indexOf(prs, "OBJECT_TYPE"); // TYPE/PACKAGE/FUNCTION ?
//            int prPN = indexOf(prs, "PROCEDURE_NAME");
//            
//            // procedure args
//            int praO = indexOf(prs, "OWNER");
//            int praON = indexOf(prs, "OBJECT_NAME");
//            int praPN = indexOf(prs, "PACKAGE_NAME");
//            int praAN = indexOf(prs, "ARGUMENT_NAME");
//            int praDT = indexOf(prs, "DATA_TYPE");
//            int praDLevel = indexOf(pras, "DATA_LEVEL");
//            int praDefaulted = indexOf(prs, "DEFAULTED");
//            int praDefault = indexOf(prs, "DEFAULT_VALUE");
//            int praDefaultLen = indexOf(prs, "DEFAULT_LENGTH");
//            int praInOut = indexOf(prs, "IN_OUT"); // IN, OUT, IN/OUT
//            int praLEN = indexOf(sas, "DATA_LENGTH");
//            int praPRE = indexOf(sas, "DATA_PRECISION");
//            int praSC = indexOf(sas, "DATA_SCALE");
//            int praCS = indexOf(sas, "CHARACTER_SET_NAME");
//            int praDTO = indexOf(prs, "TYPE_OWNER");
//            int praDTN = indexOf(prs, "TYPE_NAME");
//            int praPLS = indexOf(prs, "PLS_TYPE");
//            int praCL = indexOf(prs, "CHAR_LENGTH");
//            int praOrder = indexOf(prs, "POSITION");
                    if (dbp != null) {
                        int[] ar = rangeOf(pras, new String[]{"PACKAGE_NAME", "OBJECT_NAME"}, new Object[]{pn, prn});
                        if (ar[0] != -1) {
                            for (int i = ar[0]; i <= ar[1]; i++) {
                                APIDataType dt = null;

                                Object[] ao = pras.get(i);
                                Number dataLevel = (Number) ao[praDLevel];
                                if (dataLevel.intValue() > 0) {
                                    continue;
                                }

                                String an = (String) ao[praAN];
                                String at = (String) ao[praDT];
                                String ato = (String) ao[praDTO];
                                String atn = (String) ao[praDTN];
                                String aio = (String) ao[praInOut];

                                if (at == null) {
                                    continue;
                                }

                                boolean hasDef = !"N".equalsIgnoreCase((String) ao[praDefaulted]);
                                Object defVal = (hasDef) ? ao[praDefault] : null;
                                Number defLen = (hasDef) ? (Number) ao[praDefaultLen] : null;
                                Number aOrder = (Number) ao[praOrder];
                                Number aSeq = (Number) ao[praSeq];
                                if ("OBJECT".equalsIgnoreCase(at) || "COLLECTION".equalsIgnoreCase(at) || ato != null) {
                                    // UDT!
                                    dt = new APIDataType(APIItemCategory.object, atn, ato);
                                    if (resolved.containsKey(dt.fqn())) {
                                        try {
                                            dt = (APIDataType) resolved.get(dt.fqn());
                                        } catch (Throwable th) {
                                            if (th instanceof ClassCastException && resolved.get(dt.fqn()) instanceof DB_Package) {
                                                // if resolves to package -> not error, just not a type
                                            } else {
                                                th.printStackTrace();
                                                unresolved.put(dt.fqn(), dt);
                                            }
                                        }
                                    } else {
                                        unresolved.put(dt.fqn(), dt);
                                    }
                                } else {
                                    DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, at);
                                    Number atl = (Number) ao[praLEN];
                                    Number atp = (Number) ao[praPRE];
                                    Number ats = (Number) ao[praSC];
                                    String atcs = (String) ao[praCS];
                                    if (atl != null) {
                                        t.len = atl.intValue();
                                    }
                                    if (atp != null) {
                                        t.prec = atp.intValue();
                                    }
                                    if (ats != null) {
                                        t.scale = ats.intValue();
                                    }
                                    if (atcs != null) {
                                        t.cs = atcs;
                                    }

                                    if (dbm.types.containsKey(t.fqn())) {
                                        t = (DB_SQLType) dbm.types.get(t.fqn());
                                    } else {
                                        dbm.types.put(t.fqn(), t);
                                    }
                                    dt = t;
                                }

                                if (dt != null) {
                                    APIParameter pp = new APIParameter(an, dt);
                                    if (aOrder != null) {
                                        pp.order = aOrder.intValue();
                                    }
                                    if (aOrder != null && aOrder.intValue() == 0 || an == null && "OUT".equalsIgnoreCase(aio)) {
                                        pp.direction = APIParameterDirection.ret;
                                    } else if (aio != null) {
                                        pp.direction = ("IN".equalsIgnoreCase(aio)
                                                ? APIParameterDirection.in
                                                : "OUT".equalsIgnoreCase(aio)
                                                ? APIParameterDirection.out
                                                : APIParameterDirection.in_out);
                                    }

                                    if (dbp instanceof APIFunction && (aOrder != null && aOrder.intValue() == 0 || an == null)) {
                                        ((APIFunction) dbp).response = pp;
                                    } else {
                                        if (an == null) {
                                            // probably not a parameter... why got it here?
                                            int a = 0;
                                        } else {
                                            dbp.params.put(an, pp);
                                        }
                                    }
                                    pp.usedIn.add(dbp);
                                    if (verifier != null) {
                                        verifier.onItem(conn, pp, dbp);
                                    }

                                    // warning for unclear situation: unnnamed in parameter
                                    if (an == null && "IN".equals(aio)) {
                                        if (aSeq != null && aSeq.intValue() != 0) {
                                            System.out.println("WARNING: in '" + dbp.fqn() + "' got unnamed IN parameter[pos=" + aOrder + ", seq=" + aSeq + "]: " + pp);
                                        } else {
                                            // ignore
                                            int a = 0;
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }

            for (String tn : dbm.types.keySet().toArray(new String[dbm.types.size()])) {
                APIDataType dt = dbm.types.get(tn);
                if (dt.usedIn.isEmpty()) {
                    System.out.println("Removed unreferenced type: " + dt);
                    dbm.types.remove(dt);
                }
            }
            return dbm;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    public static interface DBItemVerifier {

        void onItem(Connection conn, APIItem item, APIItem context) throws SQLException;
    }

    static int indexOf(List<Object[]> lst, String name) {
        return indexOf((String[]) lst.get(0), name);
    }

    static int indexOf(String[] ns, String name) {
        for (int i = 0; i < ns.length; i++) {
            if (name.equalsIgnoreCase(ns[i])) {
                return i;
            }
        }
        return -1;
    }

    static int[] rangeOf(List<Object[]> lst, String[] names, Object[] values) {
        int[] r = null;
        for (int i = 0; i < Math.min(names.length, values.length); i++) {
            r = rangeOf(lst, names[i], values[i], r);
        }
        return r;
    }

    static int[] rangeOf(List<Object[]> lst, String name, Object value, int... limits) {
        int[] r = new int[]{-1, -1};
        if (lst == null || lst.isEmpty()) {
            return r;
        }
        int cIdx = indexOf((String[]) lst.get(0), name);
        if (cIdx == -1 || lst.size() < 2) {
            return r;
        }

        boolean firstFound = false;
        if (limits == null || limits.length < 2 || limits[0] == -1 || limits[1] == -1) {
            limits = new int[]{1, lst.size() - 1};
        }
        for (int i = limits[0]; i <= limits[1]; i++) {
            Object[] oo = lst.get(i);
            if (value == null && oo[cIdx] == null || value != null && value.equals(oo[cIdx])) {
                if (firstFound) {
                    r[1] = i;
                } else {
                    r[0] = i;
                    firstFound = true;
                }
            } else if (firstFound) {
                r[1] = i - 1;
                break;
            }
        }

        // fix upper bound if single last row...
        if (r[0] != -1 && r[1] == -1) {
            r[1] = r[0];
        }
        return r;
    }

    static List<Object[]> select(Connection conn, String sql) throws SQLException {
        CallableStatement cs = conn.prepareCall(sql);
        ResultSet rs = cs.executeQuery();
        return JDBCTools.rs2list(rs, false);
    }

    public static interface ProceedRange<T extends APIDataType> {

        void handle(T dt, int min, int max) throws SQLException;
    }
}
