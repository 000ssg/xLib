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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.api.APIAttr;
import ssg.lib.api.APIFunction;
import ssg.lib.api.APIItem;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.APIDataType;
import ssg.lib.api.APIGroup;
import ssg.lib.api.APIParameterDirection;
import ssg.lib.api.APIItemCategory;
import static ssg.lib.api.util.JDBCTools.rs2list;

/**
 *
 * @author 000ssg
 */
public class DB_API_Builder {

    public static DB_API buildJDBC(DB_API_Context context, String[] schemas, DBItemVerifier verifier) throws SQLException {
        try {
            DB_API dbm = context.getDBM();
            Connection conn = context.getConnection();
            Map<String, APIItem> resolved = context.getResolved();
            Map<String, APIItem> unresolved = context.getUnresolved();

            DatabaseMetaData md = conn.getMetaData();

            Map<String, APIDataType> globalTypes = dbm.types;

            List<Object[]> rsSchemas = rs2list(md.getSchemas(), true);
            List<Object[]> rsCatalogs = rs2list(md.getCatalogs(), true);
            List<Object[]> rsTypes = rs2list(md.getTypeInfo(), true);
            List<Object[]> rsAttributes = null;

            try {
                rsAttributes = rs2list(md.getAttributes(null, null, null, null), true);
            } catch (Throwable th) {
            }

            if (1 == 0) {
                System.out.println("Totals:"
                        + "\n  Schemas       : " + (rsSchemas.size() - 3) + "\n    " + JDBCTools.dumpRSList(rsSchemas, true, null, true, "TABLE_SCHEM", "TABLE_CATALOG").replace("\n", "\n    ")
                        + "\n  Catalogs      : " + (rsCatalogs.size() - 3) + "\n    " + JDBCTools.dumpRSList(rsCatalogs, true, null, true, (String[]) null).replace("\n", "\n    ")
                        + "\n  Types         : " + (rsTypes.size() - 3) + "\n    " + JDBCTools.dumpRSList(rsTypes, true, null, true, (String[]) null).replace("\n", "\n    ")
                        + ((rsAttributes != null) ? "\n  Attrs         : " + (rsAttributes.size() - 3) + "\n    " + JDBCTools.dumpRSList(rsAttributes, true, null, true, (String[]) null).replace("\n", "\n    ") : "")
                );
            }

            // prepare schemas (to allow proper cross-schema types registration)
            {
                int iSch = indexOf(rsSchemas, "TABLE_SCHEM");
                int iCat = indexOf(rsSchemas, "TABLE_CATALOG");
                for (int i = 3; i < rsSchemas.size(); i++) {
                    Object[] oo = rsSchemas.get(i);
                    String schema = (String) oo[iSch];
                    String catalog = (String) oo[iCat];
                    DB_Schema dbs = new DB_Schema(schema);
                    dbm.groups.put(schema, dbs);
                    dbs.fixUsedIn(dbm);
                    resolved.put(dbs.fqn(), dbs);
                    //System.out.println("Pre-added schema: " + schema);
                }
                if (!dbm.groups.containsKey("PUBLIC")) {
                    DB_Schema dbs = new DB_Schema("PUBLIC");
                    dbm.groups.put("PUBLIC", dbs);
                    dbs.fixUsedIn(dbm);
                    resolved.put(dbs.fqn(), dbs);
                    //System.out.println("Pre-added schema (explicitly): PUBLIC");
                }
            }

            int itN = indexOf(rsTypes, "TYPE_NAME");
            int itDT = indexOf(rsTypes, "DATA_TYPE");
            int itPrec = indexOf(rsTypes, "PRECISION");
            int itLitPrefix = indexOf(rsTypes, "LITERAL_PREFIX");
            int itLitSuffix = indexOf(rsTypes, "LITERAL_SUFFIX");
            int itCreParams = indexOf(rsTypes, "CREATE_PARAMS");
            int itNullable = indexOf(rsTypes, "NULLABLE");
            int itCS = indexOf(rsTypes, "CASE_SENSITIVE");
            int itSearchable = indexOf(rsTypes, "SEARCHABLE");
            int itUnsignAttr = indexOf(rsTypes, "UNSIGNED_ATTRIBUTE");
            int itFixPrecScale = indexOf(rsTypes, "FIXED_PREC_SCALE");
            int itAutoIncr = indexOf(rsTypes, "AUTO_INCREMENT");
            int itLN = indexOf(rsTypes, "LOCAL_TYPE_NAME");
            int itMinScale = indexOf(rsTypes, "MINIMUM_SCALE");
            int itMaxScale = indexOf(rsTypes, "MAXIMUM_SCALE");
            int itSDT = indexOf(rsTypes, "SQL_DATA_TYPE");
            int itSQLDateTimeSub = indexOf(rsTypes, "SQL_DATETIME_SUB");
            int itNPRadix = indexOf(rsTypes, "NUM_PREC_RADIX");

            // create global types...
            for (int i = 3; i < rsTypes.size(); i++) {
                Object[] oo = rsTypes.get(i);
                String tn = (String) oo[itN];
                Number dt = (Number) oo[itDT];
                Number prec = (Number) oo[itPrec];
                boolean nullable = oo[itNullable] instanceof Number && ((Number) oo[itNullable]).intValue() == 1;
                Number min = (Number) oo[itMinScale];
                Number max = (Number) oo[itMaxScale];
                Number sTN = (Number) oo[itSDT];
                //System.out.println("TYPE: " + tn + "/" + dt + "/" + prec + "/(" + min + " - " + max + ")" + "/" + nullable + "/" + sTN);
                int a = 0;

                DB_SQLType t = new DB_SQLType.DB_SQLTypeX(APIItemCategory.data_type, tn, dt.intValue());
                t.prec = prec.intValue();
                t.mandatory = !nullable;

                globalTypes.put(t.fqn(), t);
                t.fixUsedIn(dbm);
                resolved.put(t.fqn(), t);
            }

            for (String schema : schemas) {
                String cat = null;
                List<Object[]> rsTables = rs2list(md.getTables(cat, schema, null, null), true);
                List<Object[]> rsColumns = rs2list(md.getColumns(cat, schema, null, null), true);
                List<Object[]> rsProcs = rs2list(md.getProcedures(cat, schema, null), true);
                List<Object[]> rsProcCols = rs2list(md.getProcedureColumns(cat, schema, null, null), true);
                List<Object[]> rsFuncs = rs2list(md.getFunctions(cat, schema, null), true);
                List<Object[]> rsFuncCols = rs2list(md.getFunctionColumns(cat, schema, null, null), true);
//                // extra meta-data
//                List<Object[]> rsPColumns = null;
//                try {
//                    rsPColumns = rs2list(md.getPseudoColumns(cat, schema, null, null), true);
//                } catch (Throwable th) {
//                }
//                List<Object[]> rsSTables = null;
//                try {
//                    rsSTables = rs2list(md.getSuperTables(cat, schema, null), true);
//                } catch (Throwable th) {
//                }
//                List<Object[]> rsSTypes = null;
//                try {
//                    rsSTypes = rs2list(md.getSuperTypes(cat, schema, null), true);
//                } catch (Throwable th) {
//                }

//                System.out.println("    schema=" + schema
//                        + "\n      Tables        : " + rsTables.size()
//                        + "\n      Table cols    : " + rsColumns.size()
//                        + ((rsPColumns != null) ? "\n      Table ps.cols : " + rsPColumns.size() : "")
//                        + "\n      Procedures    : " + rsProcs.size()
//                        + "\n      Procedure cols: " + rsProcCols.size()
//                        + "\n      Functions     : " + rsFuncs.size()
//                        + "\n      Function cols : " + rsFuncCols.size()
//                        + ((rsSTables != null) ? "\n      Super tables  : " + rsSTables.size() : "")
//                        + ((rsSTypes != null) ? "\n      Super types   : " + rsSTypes.size() : "")
//                        + ((rsUDTs != null) ? "\n      UDT types     : " + rsUDTs.size() : "")
//                );
//
//                if (1 == 0) {
//                    System.out.println("    schema=" + schema
//                            + "\n      Tables        : " + rsTables.size() + "\n    " + JDBCTools.dumpRSList(rsTables, true, null, true, (String[]) null).replace("\n", "\n    ")
//                            + "\n      Procedures    : " + rsProcs.size() + "\n    " + JDBCTools.dumpRSList(rsProcs, true, null, true, (String[]) null).replace("\n", "\n    ")
//                            + "\n      Procedure cols: " + rsProcCols.size() + "\n    " + JDBCTools.dumpRSList(rsProcCols, true, null, true, (String[]) null).replace("\n", "\n    ")
//                            + "\n      Functions     : " + rsFuncs.size() + "\n    " + JDBCTools.dumpRSList(rsFuncs, true, null, true, (String[]) null).replace("\n", "\n    ")
//                            + ((rsUDTs != null) ? "\n      UDTs          : " + rsUDTs.size() + "\n    " + JDBCTools.dumpRSList(rsUDTs, true, null, true, (String[]) null).replace("\n", "\n    ") : "")
//                    );
//                }
                DB_Schema dbs = (DB_Schema) resolved.get(schema);
                if (dbs == null) {
                    dbs = new DB_Schema(schema);
                    dbm.groups.put(schema, dbs);
                    dbs.fixUsedIn(dbm);
                    resolved.put(dbs.fqn(), dbs);
                }

                // tables
                int ittCat = indexOf(rsTables, "TABLE_CAT");
                int ittSch = indexOf(rsTables, "TABLE_SCHEM");
                int ittN = indexOf(rsTables, "TABLE_NAME");
                int ittT = indexOf(rsTables, "TABLE_TYPE");
                int ittRem = indexOf(rsTables, "REMARKS");

                // table cols
                int itcCat = indexOf(rsColumns, "TABLE_CAT");
                int itcSch = indexOf(rsColumns, "TABLE_SCHEM");
                int itcTbl = indexOf(rsColumns, "TABLE_NAME");
                int itcN = indexOf(rsColumns, "COLUMN_NAME");
                int itcDT = indexOf(rsColumns, "DATA_TYPE");
                int itcTN = indexOf(rsColumns, "TYPE_NAME");
                int itcSz = indexOf(rsColumns, "COLUMN_SIZE");
                int itcBufLen = indexOf(rsColumns, "BUFFER_LENGTH");
                int itcDecDigs = indexOf(rsColumns, "DECIMAL_DIGITS");
                int itcNumPrecRad = indexOf(rsColumns, "NUM_PREC_RADIX");
                int itcNulable = indexOf(rsColumns, "NULLABLE");
                int itcRem = indexOf(rsColumns, "REMARKS");
                int itcDef = indexOf(rsColumns, "COLUMN_DEF");
                int itcSDT = indexOf(rsColumns, "SQL_DATA_TYPE");
                int itcSDTSub = indexOf(rsColumns, "SQL_DATETIME_SUB");
                int itcCOLen = indexOf(rsColumns, "CHAR_OCTET_LENGTH");
                int itcOrder = indexOf(rsColumns, "ORDINAL_POSITION");
                int itcNullable2 = indexOf(rsColumns, "IS_NULLABLE");
                int itcSCat = indexOf(rsColumns, "SCOPE_CATALOG");
                int itcSSch = indexOf(rsColumns, "SCOPE_SCHEMA");
                int itcSTbl = indexOf(rsColumns, "SCOPE_TABLE");
                int itcSDT2 = indexOf(rsColumns, "SOURCE_DATA_TYPE");
                int itcAutoIncr = indexOf(rsColumns, "IS_AUTOINCREMENT");

                for (int i = 3; i < rsTables.size(); i++) {
                    Object[] oo = rsTables.get(i);

                    String tCat = (String) oo[ittCat];
                    String tSch = (String) oo[ittSch];
                    String tN = (String) oo[ittN];
                    String tT = (String) oo[ittT];
                    String tRem = (String) oo[ittRem];
                    if ("TABLE".equals(tT)) {
                        DB_Table dbt = new DB_Table(tN, tSch);
                        dbs.tables.put(tN, dbt);
                        dbt.fixUsedIn(dbs);
                        resolved.put(dbt.fqn(), dbt);

                        // cols
                        int[] tii = rangeOf(rsColumns, new String[]{"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME"}, new Object[]{null, schema, tN});
                        for (int j = tii[0]; j <= tii[1]; j++) {
                            if (j == -1) {
                                break;
                            }
                            Object[] oc = rsColumns.get(j);
                            String cTCat = (String) oc[itcCat];
                            String cTSch = (String) oc[itcSch];
                            String cTbl = (String) oc[itcTbl];
                            String cn = (String) oc[itcN];
                            Number cdt = (Number) oc[itcDT];
                            String ctn = (String) oc[itcTN];
                            Number cSz = (Number) oc[itcSz];
                            Number cBufLen = (Number) oc[itcBufLen];
                            Number cDecDig = (Number) oc[itcDecDigs];
                            Number cNumPrecRad = (Number) oc[itcNumPrecRad];
                            boolean cNullable = oc[itcNulable] instanceof Number && ((Number) oc[itcNulable]).intValue() == 1;
                            String cRem = (String) oc[itcRem];
                            Object cDef = oc[itcDef];
                            Number cSDT = (Number) oc[itcSDT];
                            Number cSDTSub = (Number) oc[itcSDTSub];
                            Number cCOLen = (Number) oc[itcCOLen];
                            Number cOrder = (Number) oc[itcOrder];
                            boolean cNullable2 = oc[itcNullable2] instanceof Number && ((Number) oc[itcNullable2]).intValue() == 1;
                            String csCat = (String) oc[itcSCat];
                            String csSch = (String) oc[itcSSch];
                            String csTbl = (String) oc[itcSTbl];
                            Number cSDT2 = (Number) oc[itcSDT2];
                            boolean cAutoIncr = oc[itcAutoIncr] instanceof Number && ((Number) oc[itcAutoIncr]).intValue() == 1;

                            DB_Type t = context.smartPrepare(resolved, unresolved, ctn, cdt, cDecDig, cCOLen, cNullable);

                            DB_Col dbCol = new DB_Col(cn, t);
                            dbCol.order = cOrder.intValue();
                            dbt.columns.put(cn, dbCol);
                            dbCol.fixUsedIn(dbt);
                            t.fixUsedIn(dbCol);
                        }
                    }
                }

                // proc
                int ipCat = indexOf(rsProcs, "PROCEDURE_CAT");
                int ipSch = indexOf(rsProcs, "PROCEDURE_SCHEM");
                int ipN = indexOf(rsProcs, "PROCEDURE_NAME");
                int ipRem = indexOf(rsProcs, "REMARKS");
                int ipRet = indexOf(rsProcs, "PROCEDURE_TYPE");
                int ipSN = indexOf(rsProcs, "SPECIFIC_NAME");

                // proc cols
                int ipcCat = indexOf(rsProcCols, "PROCEDURE_CAT");
                int ipcSch = indexOf(rsProcCols, "PROCEDURE_SCHEM");
                int ipcFN = indexOf(rsProcCols, "PROCEDURE_NAME");
                int ipcN = indexOf(rsProcCols, "COLUMN_NAME");
                int ipcCT = indexOf(rsProcCols, "COLUMN_TYPE");
                int ipcDT = indexOf(rsProcCols, "DATA_TYPE");
                int ipcTN = indexOf(rsProcCols, "TYPE_NAME");
                int ipcPrec = indexOf(rsProcCols, "PRECISION");
                int ipcLen = indexOf(rsProcCols, "LENGTH");
                int ipcScale = indexOf(rsProcCols, "SCALE");
                int ipcRad = indexOf(rsProcCols, "RADIX");
                int ipcNullable = indexOf(rsProcCols, "NULLABLE");
                int ipcRem = indexOf(rsProcCols, "REMARKS");
                int ipcColDef = indexOf(rsProcCols, "COLUMN_DEF");
                int ipcSDT = indexOf(rsProcCols, "SQL_DATA_TYPE");
                int ipcSDTSub = indexOf(rsProcCols, "SQL_DATETIME_SUB");
                int ipcCOLen = indexOf(rsProcCols, "CHAR_OCTET_LENGTH");
                int ipcOrder = indexOf(rsProcCols, "ORDINAL_POSITION");
                int ipcNullable2 = indexOf(rsProcCols, "IS_NULLABLE");
                int ipcSN = indexOf(rsProcCols, "SPECIFIC_NAME");
                int ipcSeq = indexOf(rsProcCols, "SEQUENCE");
                int ipcOverload = indexOf(rsProcCols, "OVERLOAD");
                int ipcDef2 = indexOf(rsProcCols, "DEFAULT_VALUE");

                // func
                int ifCat = indexOf(rsFuncs, "FUNCTION_CAT");
                int ifSch = indexOf(rsFuncs, "FUNCTION_SCHEM");
                int ifN = indexOf(rsFuncs, "FUNCTION_NAME");
                int ifRem = indexOf(rsFuncs, "REMARKS");
                int ifRet = indexOf(rsFuncs, "FUNCTION_TYPE");
                int ifSN = indexOf(rsFuncs, "SPECIFIC_NAME");

                // func cols
                int ifcCat = indexOf(rsFuncCols, "FUNCTION_CAT");
                int ifcSch = indexOf(rsFuncCols, "FUNCTION_SCHEM");
                int ifcFN = indexOf(rsFuncCols, "FUNCTION_NAME");
                int ifcN = indexOf(rsFuncCols, "COLUMN_NAME");
                int ifcCT = indexOf(rsFuncCols, "COLUMN_TYPE");
                int ifcDT = indexOf(rsFuncCols, "DATA_TYPE");
                int ifcTN = indexOf(rsFuncCols, "TYPE_NAME");
                int ifcPrec = indexOf(rsFuncCols, "PRECISION");
                int ifcLen = indexOf(rsFuncCols, "LENGTH");
                int ifcScale = indexOf(rsFuncCols, "SCALE");
                int ifcRad = indexOf(rsFuncCols, "RADIX");
                int ifcNullable = indexOf(rsFuncCols, "NULLABLE");
                int ifcRem = indexOf(rsFuncCols, "REMARKS");
                int ifcColDef = indexOf(rsFuncCols, "COLUMN_DEF");
                int ifcSDT = indexOf(rsFuncCols, "SQL_DATA_TYPE");
                int ifcSDTSub = indexOf(rsFuncCols, "SQL_DATETIME_SUB");
                int ifcCOLen = indexOf(rsFuncCols, "CHAR_OCTET_LENGTH");
                int ifcOrder = indexOf(rsFuncCols, "ORDINAL_POSITION");
                int ifcNullable2 = indexOf(rsFuncCols, "IS_NULLABLE");
                int ifcSN = indexOf(rsFuncCols, "SPECIFIC_NAME");
                int ifcSeq = indexOf(rsFuncCols, "SEQUENCE");
                int ifcOverload = indexOf(rsFuncCols, "OVERLOAD");
                int ifcDef2 = indexOf(rsFuncCols, "DEFAULT_VALUE");

                // scan for UDTs in parameters
                {

                    for (int i = 3; i < rsProcCols.size(); i++) {
                        Object[] oc = rsProcCols.get(i);
                        Number fcDT = (Number) oc[ipcDT];
                        String fcTN = (String) oc[ipcTN];
                        Number fcScale = (Number) oc[ipcScale];
                        Number fcCOLen = (Number) oc[ipcCOLen];
                        boolean fcNullable = oc[ipcNullable] instanceof Number && ((Number) oc[ipcNullable]).intValue() == 1;

                        DB_Type t = context.smartPrepare(resolved, unresolved, fcTN, fcDT, fcScale, fcCOLen, fcNullable);
                        if (verifier != null) {
                            verifier.onItem(conn, t, dbs);
                        }
                    }
                    for (int i = 3; i < rsFuncCols.size(); i++) {
                        Object[] oc = rsFuncCols.get(i);
                        Number fcDT = (Number) oc[ifcDT];
                        String fcTN = (String) oc[ifcTN];
                        Number fcScale = (Number) oc[ifcScale];
                        Number fcCOLen = (Number) oc[ifcCOLen];
                        boolean fcNullable = oc[ifcNullable] instanceof Number && ((Number) oc[ipcNullable]).intValue() == 1;

                        DB_Type t = context.smartPrepare(resolved, unresolved, fcTN, fcDT, fcScale, fcCOLen, fcNullable);
                        if (verifier != null) {
                            verifier.onItem(conn, t, dbs);
                        }
                    }

                    context.prepareExtraTypes(dbs);
                }

                for (int i = 3; i < rsProcs.size(); i++) {
                    Object[] oo = rsProcs.get(i);
                    String fCat = (String) oo[ipCat];
                    String fSch = (String) oo[ipSch];
                    String fn = (String) oo[ipN];
                    String rem = (String) oo[ipRem];
                    Number fRet = ((Number) oo[ipRet]).intValue();
                    String fsn = (String) oo[ipSN];

                    Object p3 = oo[3];
                    Object p4 = oo[4];
                    Object p5 = oo[5];

                    // detect if this is not procedure, but function
                    boolean isFunction = false;
                    int[] tii = scan(rsProcCols, new String[]{"PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME"}, new Object[]{fCat, fSch, fn});
                    for (int j : tii) {
                        Object[] oc = rsProcCols.get(j);
                        String fcN = (String) oc[ipcN];
                        if (null == fcN) {
                            isFunction = true;
                            break;
                        }
                    }

                    APIProcedure proc = (isFunction) ? new APIFunction(fn, fSch, fCat) : new APIProcedure(fn, fSch, fCat);

                    if (fCat != null) {
                        DB_Package pkg = new DB_Package(fCat, fSch);
                        if (resolved.containsKey(pkg.fqn())) {
                            APIItem obj = resolved.get(pkg.fqn());
                            if (obj instanceof DB_ObjectType) {
                                DB_ObjectType ot = (DB_ObjectType) obj;
                                ot.methods.put(proc.name, proc);
                                proc.fixUsedIn(ot);
                            } else if (obj instanceof DB_Package) {
                                pkg = (DB_Package) obj;
                                pkg.content.put(proc.name, proc);
                                proc.fixUsedIn(pkg);
                            } else if (obj instanceof APIGroup) {
                                APIGroup group = (APIGroup) obj;
                                if (isFunction) {
                                    APIFunction[] procs = group.funcs.get(proc.name);
                                    if (procs == null) {
                                        procs = new APIFunction[]{(APIFunction) proc};
                                    } else {
                                        procs = Arrays.copyOf(procs, procs.length + 1);
                                        procs[procs.length - 1] = (APIFunction) proc;
                                    }
                                    group.funcs.put(proc.name, procs);
                                } else {
                                    APIProcedure[] procs = group.procs.get(proc.name);
                                    if (procs == null) {
                                        procs = new APIProcedure[]{proc};
                                    } else {
                                        procs = Arrays.copyOf(procs, procs.length + 1);
                                        procs[procs.length - 1] = proc;
                                    }
                                    group.procs.put(proc.name, procs);
                                }
                                proc.fixUsedIn(group);
                            }
                        } else {
                            dbs.packages.put(pkg.name, pkg);
                            pkg.fixUsedIn(dbs);
                            resolved.put(pkg.fqn(), pkg);
                            pkg.content.put(proc.name, proc);
                            proc.fixUsedIn(pkg);
                        }
                    } else {
                        if (isFunction) {
                            dbs.funcs.put(proc.name, new APIFunction[]{(APIFunction) proc});
                        } else {
                            dbs.procs.put(proc.name, new APIProcedure[]{proc});
                        }
                        proc.fixUsedIn(dbs);
                    }

                    // cols
                    for (int j : tii) {
                        Object[] oc = rsProcCols.get(j);

                        String fcCat = (String) oc[ipcCat];
                        String fcSch = (String) oc[ipcSch];
                        String fcFN = (String) oc[ipcFN];
                        String fcN = (String) oc[ipcN];
                        Number fcCT = (Number) oc[ipcCT];
                        Number fcDT = (Number) oc[ipcDT];
                        String fcTN = (String) oc[ipcTN];
                        Number fcPrec = (Number) oc[ipcPrec];
                        Number fcLen = (Number) oc[ipcLen];
                        Number fcScale = (Number) oc[ipcScale];
                        Number fcRad = (Number) oc[ipcRad];
                        boolean fcNullable = oc[ipcNullable] instanceof Number && ((Number) oc[ipcNullable]).intValue() == 1;
                        String fcRem = (String) oc[ipcRem];
                        Object fcColDef = oc[ipcColDef];
                        Number fcSDT = (Number) oc[ipcSDT];
                        Number fcSDTSub = (Number) oc[ipcSDTSub];
                        Number fcCOLen = (Number) oc[ipcCOLen];
                        Number fcOrder = (Number) oc[ipcOrder];
                        boolean fcNullable2 = oc[ipcNullable2] instanceof Number && ((Number) oc[ipcNullable2]).intValue() == 1;
                        String fcSN = (String) oc[ipcSN];
                        Number fcSeq = (Number) oc[ipcSeq];
                        Object fcOverload = oc[ipcOverload];
                        Object fcDef2 = oc[ipcDef2];

                        String key = (fSch != null ? fSch : "") + (fCat != null ? "." + fCat : "") + (fcFN != null ? "." + fcFN : "") + (fcN != null ? "." + fcN : "#");
                        DB_Type t = context.getMappedType(key);
                        if (t == null) {
                            if ("TABLE".equals(fcTN)) {
                                System.out.println("unresolved table param: " + key);
                            }
                            t = context.smartPrepare(resolved, unresolved, fcTN, fcDT, fcScale, (fcLen != null) ? fcLen : fcCOLen, fcNullable);
                        }

                        APIParameter param = new APIParameter(fcN, t);
                        if (fcN == null) {
                            ((APIFunction) proc).response = param;
                        } else {
                            proc.params.put(fcN, param);
                        }
                        param.fixUsedIn(proc);
                        switch (fcCT.intValue()) {
                            case DatabaseMetaData.functionColumnIn:
                                param.direction = APIParameterDirection.in;
                                break;
                            case DatabaseMetaData.functionColumnInOut:
                                param.direction = APIParameterDirection.in_out;
                                break;
                            case DatabaseMetaData.functionColumnOut:
                                param.direction = APIParameterDirection.out;
                                break;
                            //case DatabaseMetaData.functionColumnResult:
                            case DatabaseMetaData.functionReturn:
                                param.direction = APIParameterDirection.ret;
                                break;
                            case DatabaseMetaData.functionColumnUnknown:
                                throw new RuntimeException("Unexpected procedure column type: " + fcCT + " for " + param + " in " + proc);
                        }
                    }
                }

                for (int i = 3; i < rsFuncs.size(); i++) {
                    Object[] oo = rsFuncs.get(i);
                    String fCat = (String) oo[ifCat];
                    String fSch = (String) oo[ifSch];
                    String fn = (String) oo[ifN];
                    String rem = (String) oo[ifRem];
                    Number fRet = ((Number) oo[ifRet]).intValue();
                    String fsn = (String) oo[ifSN];
//                    System.out.println("Function: " + fSch + "." + fCat + "." + fn + "():" + fRet);

                    APIFunction proc = new APIFunction(fn, fSch, fCat);

                    if (fCat != null) {
                        DB_Package pkg = new DB_Package(fCat, fSch);
                        if (resolved.containsKey(pkg.fqn())) {
                            APIItem obj = resolved.get(pkg.fqn());
                            if (obj instanceof DB_ObjectType) {
                                DB_ObjectType ot = (DB_ObjectType) obj;
                                ot.methods.put(proc.name, proc);
                                if (proc.name.equals(ot.name) && fRet.intValue() == DatabaseMetaData.functionNoTable) {
                                    APIParameter p = new APIParameter(null, ot);
                                    proc.response = p;
                                    p.fixUsedIn(proc);
                                }
                                proc.fixUsedIn(ot);
                            } else if (obj instanceof DB_Package) {
                                pkg = (DB_Package) obj;
                                if (pkg.content.containsKey(proc.name)) {
                                    APIItem tmp = pkg.content.get(proc.name);
                                    if (tmp instanceof APIFunction) {
                                        continue;
                                    } else {
                                        throw new SQLException("Duplicated package item mismatch:\n  found: " + tmp + "\n  new  : " + proc);
                                    }
                                }
                                pkg.content.put(proc.name, proc);
                                proc.fixUsedIn(pkg);
                            } else if (obj instanceof APIGroup) {
                                APIGroup group = (APIGroup) obj;
                                APIFunction[] procs = group.funcs.get(proc.name);
                                if (procs == null) {
                                    procs = new APIFunction[]{proc};
                                } else {
                                    procs = Arrays.copyOf(procs, procs.length + 1);
                                    procs[procs.length - 1] = proc;
                                }
                                group.funcs.put(proc.name, procs);
                                proc.fixUsedIn(group);
                            }
                        } else {
                            dbs.packages.put(pkg.name, pkg);
                            pkg.fixUsedIn(dbs);
                            resolved.put(pkg.fqn(), pkg);
                            pkg.content.put(proc.name, proc);
                            proc.fixUsedIn(pkg);
                        }
                    } else {
                        if (dbs.funcs.containsKey(proc.name)) {
                            APIFunction[] tmp = dbs.funcs.get(proc.name);
                            if (tmp != null && tmp.length > 0) {
                                continue;
                            } else {
                                throw new SQLException("Duplicated schema item mismatch:\n  found: " + tmp + "\n  new  : " + proc);
                            }
                        }

                        dbs.funcs.put(proc.name, new APIFunction[]{proc});
                        proc.fixUsedIn(dbs);
                    }

                    switch (fRet.intValue()) {
                        case DatabaseMetaData.functionResultUnknown:
                            break;
                        case DatabaseMetaData.functionReturnsTable:
                            break;
                        case DatabaseMetaData.functionNoTable:
                            break;
                    }

                    // cols
                    List<Object[]> lst = rsFuncCols;
                    int[] tii = scan(lst, new String[]{"FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME"}, new Object[]{fCat, fSch, fn});
                    if (tii.length == 0) {
                        tii = scan(rsProcCols, new String[]{"PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME"}, new Object[]{fCat, fSch, fn});
                        if (tii.length > 0) {
                            lst = rsProcCols;
                        }
                    }
                    for (int j : tii) {
                        Object[] oc = lst.get(j);

                        String fcCat = (String) oc[ifcCat];
                        String fcSch = (String) oc[ifcSch];
                        String fcFN = (String) oc[ifcFN];
                        String fcN = (String) oc[ifcN];
                        Number fcCT = (Number) oc[ifcCT];
                        Number fcDT = (Number) oc[ifcDT];
                        String fcTN = (String) oc[ifcTN];
                        Number fcPrec = (Number) oc[ifcPrec];
                        Number fcLen = (Number) oc[ifcLen];
                        Number fcScale = (Number) oc[ifcScale];
                        Number fcRad = (Number) oc[ifcRad];
                        boolean fcNullable = oc[ifcNullable] instanceof Number && ((Number) oc[ifcNullable]).intValue() == 1;
                        String fcRem = (String) oc[ifcRem];
                        Object fcColDef = oc[ifcColDef];
                        Number fcSDT = (Number) oc[ifcSDT];
                        Number fcSDTSub = (Number) oc[ifcSDTSub];
                        Number fcCOLen = (Number) oc[ifcCOLen];
                        Number fcOrder = (Number) oc[ifcOrder];
                        boolean fcNullable2 = oc[ifcNullable2] instanceof Number && ((Number) oc[ifcNullable2]).intValue() == 1;
                        String fcSN = (String) oc[ifcSN];
                        Number fcSeq = (Number) oc[ifcSeq];
                        Object fcOverload = oc[ifcOverload];
                        Object fcDef2 = oc[ifcDef2];

                        //DB_Type t = context.smartPrepare(resolved, unresolved, fcTN, fcDT, fcScale, (fcLen != null) ? fcLen : fcCOLen, fcNullable);
                        String key = (fSch != null ? fSch : "") + (fCat != null ? "." + fCat : "") + (fcFN != null ? "." + fcFN : "") + (fcN != null ? "." + fcN : "#");
                        DB_Type t = context.getMappedType(key);
                        if (t == null) {
                            if ("TABLE".equals(fcTN)) {
                                System.out.println("unresolved table param: " + key);
                            }
                            t = context.smartPrepare(resolved, unresolved, fcTN, fcDT, fcScale, (fcLen != null) ? fcLen : fcCOLen, fcNullable);
                        }

                        APIParameter param = new APIParameter(fcN, t);
                        param.fixUsedIn(proc);

                        switch (fcCT.intValue()) {
                            //case DatabaseMetaData.functionColumnResult:
                            case DatabaseMetaData.functionReturn:
                                param.direction = APIParameterDirection.ret;
                                proc.response = param;
                                break;
                            case DatabaseMetaData.functionColumnIn:
                                param.direction = APIParameterDirection.in;
                                proc.params.put(fcN, param);
                                break;
                            case DatabaseMetaData.functionColumnInOut:
                                param.direction = APIParameterDirection.in_out;
                                proc.params.put(fcN, param);
                                break;
                            case DatabaseMetaData.functionColumnOut:
                                param.direction = APIParameterDirection.out;
                                proc.params.put(fcN, param);
                                break;
                            case DatabaseMetaData.functionColumnUnknown:
                                throw new RuntimeException("Unexpected function column type: " + fcCT + " for " + param + " in " + proc);
                        }

                        if (param.direction == null) {
                            int a = 0;
                        }
                    }
                }
            }

            // clean up -> remove empty schemas...
            for (String schema : dbm.groups.keySet().toArray(new String[dbm.groups.size()])) {
                APIGroup group = dbm.groups.get(schema);
                if (group.isEmpty()) {
                    //System.out.println("... removed empty schema: " + schema);
                    dbm.groups.remove(schema);
                }
            }

            return dbm;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    public static DB_API buildOracle(DB_API_Context context, String[] schemas, DBItemVerifier verifier) throws SQLException {
        try {
            Connection conn = context.getConnection();
            Map<String, APIItem> resolved = context.getResolved();
            Map<String, APIItem> unresolved = context.getUnresolved();

            DB_API dbm = context.getDBM();
            // prepare schemas
            {
                List<DB_Schema> dbss = context.prepareSchemas();
                for (DB_Schema dbs : dbss) {
                    if (!dbm.groups.containsKey(dbs.name)) {
                        dbm.groups.put(dbs.name, dbs);
                        dbs.fixUsedIn(dbm);
                        resolved.put(dbs.fqn(), dbs);
                    }
                }
                if (!dbm.groups.containsKey("PUBLIC")) {
                    DB_Schema dbs = new DB_Schema("PUBLIC");
                    dbm.groups.put("PUBLIC", dbs);
                    dbs.fixUsedIn(dbm);
                    resolved.put(dbs.fqn(), dbs);
                }
            }

            for (String schema : schemas) {
                schema = schema.toUpperCase();
                String sqlTbl = "SELECT * FROM ALL_TABLES where owner='" + schema + "' order by owner";
                String sqlTblCols = "SELECT * FROM ALL_TAB_COLS where owner='" + schema + "' order by owner, table_name, column_id";

                String sqlT = "SELECT * FROM ALL_TYPES order by owner, type_name";
                String sqlCT = "SELECT * FROM ALL_COLL_TYPES where owner='" + schema + "' order by owner, type_name";
                String sqlTA = "SELECT * FROM ALL_TYPE_ATTRS order by owner, type_name, attr_no";

                String sqlPR = "SELECT * FROM ALL_PROCEDURES where owner='" + schema + "' order by owner";
                String sqlPRARG = "SELECT * FROM ALL_ARGUMENTS where owner='" + schema + "' order by owner, PACKAGE_NAME, OBJECT_NAME, sequence";

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

                DB_Schema dbs = (DB_Schema) resolved.get(schema);
                if (dbs == null) {
                    dbs = new DB_Schema(schema);
                    dbm.groups.put(schema, dbs);
                    dbs.usedIn.add(dbm);
                }

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
                int scsNL = indexOf(scs, "NULLS_STORED");

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
                int praDTO = indexOf(pras, "TYPE_OWNER"); // if UDT
                int praDTN = indexOf(pras, "TYPE_NAME"); // UDT name in OWNER schema
                int praDTSN = indexOf(pras, "TYPE_SUBNAME"); // collection item?
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
                            //String dtnull = (String) co[tcsNL];
                            boolean nullable = "Y".equals(co[tcsNL]);
                            if (dtn == null) {
                                continue;
                            }
                            Number cord = (Number) co[tcsOrder];

                            DB_Type dt = null;
                            {
                                Number atl = (Number) co[tcsLEN];
                                Number atp = (Number) co[tcsPRE];
                                Number ats = (Number) co[tcsSC];
                                String atcs = (String) co[tcsCS];
                                dt = context.smartPrepare(
                                        resolved,
                                        unresolved,
                                        (dto != null) ? dto + "." + dtn : dtn,
                                        context.name2Type(dtn),
                                        ats,
                                        atl,
                                        nullable);
                                if (atp != null) {
                                    dt.prec = atp.intValue();
                                }
                                dt.cs = atcs;
                            }
                            DB_Col da = new DB_Col(cn, dt);
                            if (cord != null) {
                                da.order = cord.intValue();
                            }
                            dt.usedIn.add(da);
                            if (verifier != null) {
                                verifier.onItem(conn, dt, da);
                            }
                            tbl.columns.put(cn, da);
                            da.usedIn.add(tbl);
                            if (verifier != null) {
                                verifier.onItem(conn, da, tbl);
                            }

//                            if (dto != null) {
//                                // UDT!
//                                da = new DB_Col(cn);
//                                if (cord != null) {
//                                    da.order = cord.intValue();
//                                }
//                                tbl.columns.put(cn, da);
//                                da.usedIn.add(tbl);
//                                if (verifier != null) {
//                                    verifier.onItem(conn, da, tbl);
//                                }
//
//                                APIDataType dt = new APIDataType(APIItemCategory.object, dtn, dto);
//                                if (resolved.containsKey(dt.fqn())) {
//                                    dt = (APIDataType) resolved.get(dt.fqn());
//                                } else {
//                                    unresolved.put(dt.fqn(), dt);
//                                }
//                                da.type = dt;
//                                dt.usedIn.add(da);
//                                if (verifier != null) {
//                                    verifier.onItem(conn, dt, da);
//                                }
//                            } else {
//                                DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, dtn);
//                                Number atl = (Number) co[tcsLEN];
//                                Number atp = (Number) co[tcsPRE];
//                                Number ats = (Number) co[tcsSC];
//                                String atcs = (String) co[tcsCS];
//                                if (atl != null) {
//                                    t.len = atl.intValue();
//                                }
//                                if (atp != null) {
//                                    t.prec = atp.intValue();
//                                }
//                                if (ats != null) {
//                                    t.scale = ats.intValue();
//                                }
//                                if (atcs != null) {
//                                    t.cs = atcs;
//                                }
//
//                                if (dbm.types.containsKey(t.fqn())) {
//                                    t = (DB_SQLType) dbm.types.get(t.fqn());
//                                } else {
//                                    dbm.types.put(t.fqn(), t);
//                                }
//                                da = new DB_Col(cn, t);
//                                if (cord != null) {
//                                    da.order = cord.intValue();
//                                }
//                                t.usedIn.add(da);
//                                if (verifier != null) {
//                                    verifier.onItem(conn, t, da);
//                                }
//                                tbl.columns.put(cn, da);
//                                da.usedIn.add(tbl);
//                                if (verifier != null) {
//                                    verifier.onItem(conn, da, tbl);
//                                }
//                            }
//                            {
//                                APIDataType ot = da.type;
//                                APIDataType mt = ddd;
//                                String ots = ot.fqn();
//                                String mts = mt.fqn();
//                                if (!ots.equals(mts)) {
//                                    System.out.println("TCS: " + tn + "." + da.name + ":" + dtn + ((dto != null) ? " (" + dto + ")" : "") + "\n  " + ots + "\n  " + mts);
//                                }
//                                int a = 0;
//                            }
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
                        boolean nullable = false;//"Y".equals(ao[sasNL]);
                        if (atn == null) {
                            continue;
                        }
                        Number atord = (Number) ao[sasOrder];

                        DB_Type dt = null;
                        {
                            Number atl = (Number) ao[sasLEN];
                            Number atp = (Number) ao[sasPRE];
                            Number ats = (Number) ao[sasSC];
                            String atcs = (String) ao[sasCS];
                            dt = context.smartPrepare(
                                    resolved,
                                    unresolved,
                                    (ato != null) ? ato + "." + atn : atn,
                                    context.name2Type(atn),
                                    ats,
                                    atl,
                                    nullable);
                            if (atp != null) {
                                dt.prec = atp.intValue();
                            }
                            dt.cs = atcs;
                        }

                        APIAttr da = new APIAttr(an, dt);
                        if (atord != null) {
                            da.order = atord.intValue();
                        }
                        dt.usedIn.add(da);
                        if (verifier != null) {
                            verifier.onItem(conn, dt, da);
                        }
                        dbo.attrs.put(an, da);
                        da.usedIn.add(dbo);
                        if (verifier != null) {
                            verifier.onItem(conn, da, dbo);
                        }

//                        if (ato != null) {
//                            // UDT!
//                            APIAttr da = new APIAttr(an);
//                            if (atord != null) {
//                                da.order = atord.intValue();
//                            }
//                            dbo.attrs.put(an, da);
//                            da.usedIn.add(dbo);
//                            if (verifier != null) {
//                                verifier.onItem(conn, da, dbo);
//                            }
//
//                            APIDataType dt = new APIDataType(APIItemCategory.object, atn, ato);
//                            if (resolved.containsKey(dt.fqn())) {
//                                dt = (APIDataType) resolved.get(dt.fqn());
//                            } else {
//                                unresolved.put(dt.fqn(), dt);
//                            }
//                            da.type = dt;
//                            dt.usedIn.add(da);
//                            if (verifier != null) {
//                                verifier.onItem(conn, dt, da);
//                            }
//                        } else {
//                            DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, atn);
//                            Number atl = (Number) ao[sasLEN];
//                            Number atp = (Number) ao[sasPRE];
//                            Number ats = (Number) ao[sasSC];
//                            String atcs = (String) ao[sasCS];
//                            if (atl != null) {
//                                t.len = atl.intValue();
//                            }
//                            if (atp != null) {
//                                t.prec = atp.intValue();
//                            }
//                            if (ats != null) {
//                                t.scale = ats.intValue();
//                            }
//                            if (atcs != null) {
//                                t.cs = atcs;
//                            }
//
//                            if (dbm.types.containsKey(t.fqn())) {
//                                t = (DB_SQLType) dbm.types.get(t.fqn());
//                            } else {
//                                dbm.types.put(t.fqn(), t);
//                            }
//                            APIAttr da = new APIAttr(an, t);
//                            if (atord != null) {
//                                da.order = atord.intValue();
//                            }
//                            t.usedIn.add(da);
//                            if (verifier != null) {
//                                verifier.onItem(conn, t, da);
//                            }
//                            dbo.attrs.put(an, da);
//                            da.usedIn.add(dbo);
//                            if (verifier != null) {
//                                verifier.onItem(conn, da, dbo);
//                            }
//                        }
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

                        APIGroup target = (dbm.groups.get(to) instanceof APIGroup) ? dbm.groups.get(to) : dbm;
                        target.types.put(tn, dbo);
                        dbo.usedIn.add(target);
                        if (verifier != null) {
                            verifier.onItem(conn, dbo, target);
                        }

//                        if (schema.equals(to)) {
//                            dbs.types.put(tn, dbo);
//                        } else {
//                            dbm.types.put(dbo.fqn(), dbo);
//                        }
//                        dbo.usedIn.add(dbs);
//                        if (verifier != null) {
//                            verifier.onItem(conn, dbo, dbs);
//                        }
                        if (tAs.intValue() > 0) {
                            int[] ar = rangeOf(sas, new String[]{"OWNER", "TYPE_NAME"}, new String[]{to, tn});
                            if (ar[0] != -1) {
                                proceedObjectAttrs.handle(dbo, ar[0], ar[1]);
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
                        APIGroup target = (dbm.groups.get(to) instanceof APIGroup) ? dbm.groups.get(to) : dbm;
                        target.types.put(tn, dbo);
                        if (verifier != null) {
                            verifier.onItem(conn, dbo, target);
                        }

                        int[] ar = rangeOf(scs, new String[]{"OWNER", "TYPE_NAME"}, new String[]{to, tn});
                        if (ar[0] != -1) {
                            APIDataType dt = null;
                            Object[] co = scs.get(ar[0]);
                            String ato = (String) co[scsETO];
                            String atn = (String) co[scsETN];
                            boolean nullable = "YES".equals(co[scsNL]);
                            if (atn == null) {
                                continue;
                            }

                            dt = null;
                            {
                                Number atl = (Number) co[scsLEN];
                                Number atp = (Number) co[scsPRE];
                                Number ats = (Number) co[scsSC];
                                String atcs = (String) co[scsCS];
                                dt = context.smartPrepare(
                                        resolved,
                                        unresolved,
                                        (ato != null) ? ato + "." + atn : atn,
                                        context.name2Type(atn),
                                        ats,
                                        atl,
                                        nullable);
                                if (dt instanceof DB_Type) {
                                    if (atp != null) {
                                        ((DB_Type) dt).prec = atp.intValue();
                                    }
                                    ((DB_Type) dt).cs = atcs;
                                }
                            }

//                            if (ato != null) {
//                                // UDT!
//                                dt = new APIDataType(APIItemCategory.object, atn, ato);
//                                if (resolved.containsKey(dt.fqn())) {
//                                    dt = (APIDataType) resolved.get(dt.fqn());
//                                } else {
//                                    unresolved.put(dt.fqn(), dt);
//                                }
//                            } else {
//                                DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, atn);
//                                Number atl = (Number) co[scsLEN];
//                                Number atp = (Number) co[scsPRE];
//                                Number ats = (Number) co[scsSC];
//                                String atcs = (String) co[scsCS];
//                                if (atl != null) {
//                                    t.len = atl.intValue();
//                                }
//                                if (atp != null) {
//                                    t.prec = atp.intValue();
//                                }
//                                if (ats != null) {
//                                    t.scale = ats.intValue();
//                                }
//                                if (atcs != null) {
//                                    t.cs = atcs;
//                                }
//
//                                if (dbm.types.containsKey(t.fqn())) {
//                                    t = (DB_SQLType) dbm.types.get(t.fqn());
//                                } else {
//                                    dbm.types.put(t.fqn(), t);
//                                }
//                                dt = t;
//                            }
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
                    boolean pnFixed = false;

                    // fix naming if function and no procedure name -> function name is object name...
                    if (prn == null && ("FUNCTION".equals(pt) || "PROCEDURE".equals(pt))) {
                        prn = pn;
                        pn = null;
                        pnFixed = true;
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
                    } else if ("PROCEDURE".equalsIgnoreCase(pt)) {
                        dbp = new APIProcedure(prn, po);
                        resolved.put(dbp.fqn(), dbp);
                        dbs.procs.put(prn, new APIProcedure[]{dbp});
                        dbp.usedIn.add(dbs);
                        if (verifier != null) {
                            verifier.onItem(conn, dbp, dbs);
                        }
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
//            int praDTSN = indexOf(pras, "TYPE_SUBNAME");
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
                                String atsn = (String) ao[praDTSN];
                                String aio = (String) ao[praInOut];
                                boolean nullable = "Y".equals(ao[praDefaulted]);

                                if (at == null) {
                                    continue;
                                }

                                try {
                                    dt = null;
                                    Number atl = (Number) ao[praLEN];
                                    Number atp = (Number) ao[praPRE];
                                    Number ats = (Number) ao[praSC];
                                    String atcs = (String) ao[praCS];
                                    dt = context.smartPrepare(
                                            resolved,
                                            unresolved,
                                            (ato != null) ? ato + "." + atn + (atsn != null ? "." + atsn : "") : at,
                                            context.name2Type((ato != null) ? atn + (atsn != null ? "." + atsn : "") : at),
                                            ats,
                                            atl,
                                            nullable);
                                    if (dt instanceof DB_Type) {
                                        if (atp != null) {
                                            ((DB_Type) dt).prec = atp.intValue();
                                        }
                                    }
                                    ((DB_Type) dt).cs = atcs;
                                } catch (Throwable th) {
                                    APIItem tmp = new APIDataType(APIItemCategory.object, atn, ato);
                                    if (th instanceof ClassCastException && resolved.get(tmp.fqn()) instanceof DB_Package) {
                                        // if resolves to package -> not error, just not a type
                                    } else {
                                        th.printStackTrace();
                                        unresolved.put(dt.fqn(), dt);
                                    }
                                }

                                boolean hasDef = !"N".equalsIgnoreCase((String) ao[praDefaulted]);
                                Object defVal = (hasDef) ? ao[praDefault] : null;
                                Number defLen = (hasDef) ? (Number) ao[praDefaultLen] : null;
                                Number aOrder = (Number) ao[praOrder];
                                Number aSeq = (Number) ao[praSeq];
//                                if ("OBJECT".equalsIgnoreCase(at) || "COLLECTION".equalsIgnoreCase(at) || ato != null) {
//                                    // UDT!
//                                    dt = new APIDataType(APIItemCategory.object, atn, ato);
//                                    if (resolved.containsKey(dt.fqn())) {
//                                        try {
//                                            dt = (APIDataType) resolved.get(dt.fqn());
//                                        } catch (Throwable th) {
//                                            if (th instanceof ClassCastException && resolved.get(dt.fqn()) instanceof DB_Package) {
//                                                // if resolves to package -> not error, just not a type
//                                            } else {
//                                                th.printStackTrace();
//                                                unresolved.put(dt.fqn(), dt);
//                                            }
//                                        }
//                                    } else {
//                                        unresolved.put(dt.fqn(), dt);
//                                    }
//                                } else {
//                                    DB_SQLType t = new DB_SQLType(APIItemCategory.data_type, at);
//                                    Number atl = (Number) ao[praLEN];
//                                    Number atp = (Number) ao[praPRE];
//                                    Number ats = (Number) ao[praSC];
//                                    String atcs = (String) ao[praCS];
//                                    if (atl != null) {
//                                        t.len = atl.intValue();
//                                    }
//                                    if (atp != null) {
//                                        t.prec = atp.intValue();
//                                    }
//                                    if (ats != null) {
//                                        t.scale = ats.intValue();
//                                    }
//                                    if (atcs != null) {
//                                        t.cs = atcs;
//                                    }
//
//                                    if (dbm.types.containsKey(t.fqn())) {
//                                        t = (DB_SQLType) dbm.types.get(t.fqn());
//                                    } else {
//                                        dbm.types.put(t.fqn(), t);
//                                    }
//                                    dt = t;
//                                }

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
                if (dt.usedIn == null || dt.usedIn.isEmpty()) {
                    System.out.println("Removed unreferenced type: " + dt);
                    dbm.types.remove(dt);
                }
            }

            // clean up -> remove empty schemas...
            for (String schema : dbm.groups.keySet().toArray(new String[dbm.groups.size()])) {
                APIGroup group = dbm.groups.get(schema);
                if (group.isEmpty()) {
                    //System.out.println("... removed empty schema: " + schema);
                    dbm.groups.remove(schema);
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

    public static int indexOf(List<Object[]> lst, String name) {
        return indexOf((String[]) lst.get(0), name);
    }

    public static int indexOf(String[] ns, String name) {
        for (int i = 0; i < ns.length; i++) {
            if (name.equalsIgnoreCase(ns[i])) {
                return i;
            }
        }
        return -1;
    }

    public static int[] rangeOf(List<Object[]> lst, String[] names, Object[] values) {
        int[] r = null;
        for (int i = 0; i < Math.min(names.length, values.length); i++) {
            r = rangeOf(lst, names[i], values[i], r);
        }
        return r;
    }

    public static int[] rangeOf(List<Object[]> lst, String name, Object value, int... limits) {
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

    /**
     * Scans all rows for given name/value within optional set of
     * indices.Returns matching indices only.
     *
     * @param lst
     * @param name
     * @param value
     * @return
     */
    public static int[] scan(List<Object[]> lst, String[] name, Object[] value) {
        if (lst == null || lst.size() < 3 || name == null || name.length == 0 || value == null || value.length != name.length) {
            return new int[0];
        }

        int[] cIdxs = new int[name.length];
        for (int i = 0; i < cIdxs.length; i++) {
            if (name[i] == null) {
                continue;
            }
            cIdxs[i] = indexOf((String[]) lst.get(0), name[i]);
            if (cIdxs[i] == -1) {
                // no column to check - > no result!
                return new int[0];
            }
        }

        // scan rows
        int[] r = new int[lst.size()];
        int off = 0;
        for (int i = 3; i < lst.size(); i++) {
            Object[] oo = lst.get(i);
            boolean ok = true;
            // scan testable rows
            for (int j = 0; j < cIdxs.length; j++) {
                if (cIdxs[j] == -1) {
                    continue;
                }
                int cIdx = cIdxs[j];
                if (value[j] == null && oo[cIdx] == null || value[j] != null && value[j].equals(oo[cIdx])) {
                    // continue, it is OK so far...
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                r[off++] = i;
            }
        }

        if (off < r.length) {
            r = Arrays.copyOf(r, off);
        }

        return r;
    }

    public static List<Object[]> select(Connection conn, String sql) throws SQLException {
        try ( CallableStatement cs = conn.prepareCall(sql);  ResultSet rs = cs.executeQuery();) {
            return JDBCTools.rs2list(rs, false);
        }
    }

    public static interface ProceedRange<T extends APIDataType> {

        void handle(T dt, int min, int max) throws SQLException;
    }

    public static interface PrepareDBType {
//
//        static Number name2Type(String name) {
//            if (name == null) {
//                return Types.OTHER;
//            } else if (name.startsWith("VARCHAR")) {
//                return Types.VARCHAR;
//            } else if (name.startsWith("NUMBER")) {
//                return Types.DECIMAL;
//            } else if (name.startsWith("LONG")) {
//                return Types.INTEGER;
//            } else if (name.startsWith("INTEGER")) {
//                return Types.INTEGER;
//            } else if (name.startsWith("FLOAT")) {
//                return Types.FLOAT;
//            } else if (name.startsWith("REAL")) {
//                return Types.REAL;
//            } else if (name.startsWith("DATE")) {
//                return Types.DATE;
//            } else if (name.startsWith("TIMESTAMP")) {
//                return Types.TIMESTAMP;
//            } else {
//                return Types.OTHER;
//            }
//        }
//
//        static <T extends DB_Type> T smartPrepare(
//                DB_API dbm,
//                Map<String, APIItem> resolved,
//                Map<String, APIItem> unresolved,
//                String typeName,
//                Number dataType,
//                Number scale,
//                Number length,
//                boolean nullable) {
//            String[] scope = null;
//            DB_Schema target = null;
//            if (typeName.contains(".")) {
//                String[] ss = typeName.split("\\.");
//                typeName = ss[ss.length - 1];
//                scope = Arrays.copyOf(ss, ss.length - 1);
//                if (scope.length == 1 && resolved.get(scope[0]) instanceof DB_Schema) {
//                    target = (DB_Schema) resolved.get(scope[0]);
//                }
//            }
//
//            DB_Type t = null;
//            if (scope != null) {
//                // UDT
//                t = new DB_ObjectType(typeName, scope);
//            } else if ("TABLE".equals(typeName) && dataType.intValue() > 0) {
//                t = new DB_CollectionType(typeName, scope);
//            } else {
//                t = new DB_SQLType.DB_SQLTypeX(APIItemCategory.data_type, typeName, dataType.intValue());
//            }
//            if (scale != null) {
//                t.prec = scale.intValue();
//            }
//            if (length != null) {
//                t.len = length.intValue();
//            }
//            t.mandatory = !nullable;
//            if (resolved.containsKey(t.fqn())) {
//                t = (DB_Type) resolved.get(t.fqn());
//            } else {
//                if (target != null) {
//                    target.types.put(t.name, t);
//                    t.fixUsedIn(target);
//                    resolved.put(t.fqn(), t);
//                } else {
//                    if (scope != null && scope.length > 0) {
//                        unresolved.put(t.name, t);
//                    } else {
//                        dbm.types.put(t.fqn(), t);
//                        t.fixUsedIn(dbm);
//                        resolved.put(t.fqn(), t);
//                    }
//                }
//            }
//            return (T) t;
//
//        }

        <T extends DB_Type> T prepareType(DB_API dbm, DB_Schema dbs);
    }

    public static interface DB_TypeResolver {

        DB_Type verifyType(DB_API dbm, DB_Type type) throws SQLException;
    }

    public static class DB_API_Context {

        Map<Integer, String> typeNames = new LinkedHashMap<>();
        Map<String, Integer> typeCodes = new LinkedHashMap<>();

        DB_API dbm;
        Connection connection;
        DB_TypeResolver typeResolver;
        DatabaseMetaData metaData;

        Map<String, APIItem> resolved = new HashMap<>();
        Map<String, APIItem> unresolved = new HashMap<>();
        Map<String, DB_Type> mappedTypes = new HashMap<>();

        public DB_API_Context(DB_API dbm, Connection connection) {
            this.dbm = dbm;
            setConnection(connection);
            initTypes();
        }

        public DB_API_Context(DB_API dbm, Connection connection, DB_TypeResolver typeResolver) {
            this.dbm = dbm;
            setConnection(connection);
            setTypeResolver(typeResolver);
            initTypes();
        }

        public void initTypes() {
            for (Object[] oo : new Object[][]{
                {"VARCHAR", Types.VARCHAR},
                {"NUMBER", Types.DECIMAL},
                {"LONG", Types.INTEGER},
                {"INTEGER", Types.INTEGER},
                {"FLOAT", Types.FLOAT},
                {"REAL", Types.REAL},
                {"DATE", Types.DATE},
                {"TIMESTAMP", Types.TIMESTAMP}
            }) {
                String n = (String) oo[0];
                int c = ((Number) oo[1]).intValue();
                typeNames.put(c, n);
                typeCodes.put(n, c);
            }
        }

        public DB_API getDBM() {
            return dbm;
        }

        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        public Connection getConnection() {
            return connection;
        }

        public void setTypeResolver(DB_TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
        }

        public DB_TypeResolver getTypeResolver() {
            return typeResolver;
        }

        public Map<String, APIItem> getResolved() {
            return resolved;
        }

        public Map<String, APIItem> getUnresolved() {
            return unresolved;
        }

        public DatabaseMetaData getMetaData() throws SQLException {
            if (metaData == null) {
                metaData = getConnection().getMetaData();
            }
            return metaData;
        }

        public List<DB_Schema> prepareSchemas() throws SQLException {
            List<DB_Schema> r = new ArrayList<>();
            if (getMetaData() != null) {
                List<Object[]> rsSchemas = rs2list(getMetaData().getSchemas(), true);
                // prepare schemas (to allow proper cross-schema types registration)
                {
                    int iSch = indexOf(rsSchemas, "TABLE_SCHEM");
                    int iCat = indexOf(rsSchemas, "TABLE_CATALOG");
                    for (int i = 3; i < rsSchemas.size(); i++) {
                        Object[] oo = rsSchemas.get(i);
                        String schema = (String) oo[iSch];
                        String catalog = (String) oo[iCat];
                        DB_Schema dbs = new DB_Schema(schema);
                        r.add(dbs);
                    }
                }
            }
            return r;
        }

        public void prepareExtraTypes(DB_Schema dbs) throws SQLException {
        }

        public Number name2Type(String name) {
            if (name == null) {
                return Types.OTHER;
            } else if (name.startsWith("VARCHAR")) {
                return Types.VARCHAR;
            } else if (name.startsWith("NUMBER")) {
                return Types.DECIMAL;
            } else if (name.startsWith("LONG")) {
                return Types.INTEGER;
            } else if (name.startsWith("INTEGER")) {
                return Types.INTEGER;
            } else if (name.startsWith("FLOAT")) {
                return Types.FLOAT;
            } else if (name.startsWith("REAL")) {
                return Types.REAL;
            } else if (name.startsWith("DATE")) {
                return Types.DATE;
            } else if (name.startsWith("TIMESTAMP")) {
                return Types.TIMESTAMP;
            } else {
                Number n = typeCodes.get(name);
                if (n != null) {
                    return n.intValue();
                } else {
                    return Types.OTHER;
                }
            }
        }

        public String type2name(int type) {
            return typeNames.get(type);
        }

        public void mapType(String id, DB_Type type) {
            if (id != null) {
                if (type != null) {
                    mappedTypes.put(id, type);
                } else if (mappedTypes.containsKey(id)) {
                    mappedTypes.remove(id);
                }
            }
        }

        public DB_Type getMappedType(String id) {
            return mappedTypes.get(id);
        }

        public Map<String, DB_Type> getMappedTypes() {
            return mappedTypes;
        }

        public boolean isUDTType(String typeName, Number dataType, String[] scope) {
            return scope != null;
        }

        /**
         * Detect if represents collection/array type
         *
         * @param typeName
         * @param dataType
         * @return
         */
        public boolean isCollectionType(String typeName, Number dataType) {
            return "TABLE".equals(typeName) && dataType.intValue() > 0;
        }

        public <T extends DB_Type> T smartPrepare(
                Map<String, APIItem> resolved,
                Map<String, APIItem> unresolved,
                String typeName,
                Number dataType,
                Number scale,
                Number length,
                boolean nullable) throws SQLException {
            String[] scope = null;
            DB_Schema target = null;
            if (typeName.contains(".")) {
                String[] ss = typeName.split("\\.");
                typeName = ss[ss.length - 1];
                scope = Arrays.copyOf(ss, ss.length - 1);
                if (scope.length == 1 && resolved.get(scope[0]) instanceof DB_Schema) {
                    target = (DB_Schema) resolved.get(scope[0]);
                }
            }

            DB_Type t = null;
            if (isUDTType(typeName, dataType, scope)) {
                // UDT
                t = new DB_ObjectType(typeName, scope);
            } else if (isCollectionType(typeName, dataType)) {
                t = new DB_CollectionType(typeName, scope);
            } else {
                t = new DB_SQLType.DB_SQLTypeX(APIItemCategory.data_type, typeName, dataType.intValue());
            }
            if (scale != null) {
                t.prec = scale.intValue();
            }
            if (length != null) {
                t.len = length.intValue();
            }
            t.mandatory = !nullable;
            if (resolved.containsKey(t.fqn())) {
                t = (DB_Type) resolved.get(t.fqn());
            } else {
                if (target != null) {
                    target.types.put(t.name, t);
                    t.fixUsedIn(target);
                    resolved.put(t.fqn(), t);
                } else {
                    if (scope != null && scope.length > 0) {
                        unresolved.put(t.name, t);
                    } else {
                        dbm.types.put(t.fqn(), t);
                        t.fixUsedIn(dbm);
                        resolved.put(t.fqn(), t);
                    }
                }
            }
            if (t instanceof DB_Type && getTypeResolver() != null) {
                t = getTypeResolver().verifyType(dbm, (DB_Type) t);
            }
            return (T) t;
        }
    }

}
