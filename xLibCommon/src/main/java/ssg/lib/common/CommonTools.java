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
package ssg.lib.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import ssg.lib.common.Refl.ReflImpl;

/**
 *
 * @author 000ssg
 */
public class CommonTools {

    static Refl refl = new ReflImpl();

    /**
     * General type converter entry point based on default Refl.enrich
     * implementation.
     *
     * @param <T>
     * @param obj
     * @param type
     * @param xtypes
     * @return
     * @throws IOException
     */
    public static <T> T toType(Object obj, Class type, Type... xtypes) throws IOException {
        return refl.enrich(obj, type, xtypes);
    }

    /**
     * Returns provided data as list. If no data - null, ff data IS the single
     * list, returns it, else creates ArrayList and fills it from data.
     *
     * @param <T>
     * @param data
     * @return
     */
    public static <T> List<T> toList(Collection<T>... data) {
        if (data == null || data.length == 0) {
            return null;
        }
        if (data.length == 1 && data[0] instanceof List) {
            return (List) data[0];
        }
        List<T> r = new ArrayList<>();
        for (Collection<T> d : data) {
            if (d != null) {
                r.addAll(d);
            }
        }
        return r;
    }

    /**
     * Returns provided data as list. If no data - null, ff data IS the single
     * list, returns it, else creates ArrayList and fills it from data.
     *
     * @param <T>
     * @param data
     * @return
     */
    public static <T> List<T> toList(T... data) {
        if (data == null || data.length == 0) {
            return null;
        }
        if (data.length == 1 && data[0] instanceof List) {
            return (List) data[0];
        }
        List<T> r = new ArrayList<>();
        for (T d : data) {
            if (d != null) {
                r.add(d);
            }
        }
        return r;
    }

    public static byte[] loadInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int c = 0;
        while ((c = is.read(buf)) != -1) {
            baos.write(buf, 0, c);;
        }
        is.close();
        baos.close();
        return baos.toByteArray();
    }

    /**
     *
     * @param starter
     * @param ender
     * @param is
     * @return
     * @throws IOException
     */
    public static byte[][] scanUniqueSubsequences(byte[] starter, byte[] ender, InputStream is) throws IOException {
        byte[][] r = null;
        int sLen = starter.length;
        int eLen = ender.length;
        int sIdx = 0;
        int eIdx = 0;

        ByteArrayOutputStream baos = null;
        int c = 0;
        while ((c = is.read()) != -1) {
            byte b = (byte) c;
            //System.out.println("scanner: "+(char)c+"  0x"+Integer.toHexString(c)+"  "+c);
            // check if sequence starter...
            if (sIdx < sLen) {
                if (starter[sIdx] == b) {
                    sIdx++;
                    if (sIdx == sLen) {
                        if (baos == null) {
                            baos = new ByteArrayOutputStream();
                        } else {
                            baos.reset();
                        }
                        baos.write(starter);
                    }
                } else {
                    sIdx = 0;
                }
            } else // check if byte closes sequence -> add new uniqe and reset
            if (sIdx == sLen) {
                if (eIdx < eLen) {
                    if (ender[eIdx] == b) {
                        eIdx++;
                    }
                    baos.write(0xFF & b);
                    if (eIdx == eLen) {
                        byte[] buf = baos.toByteArray();
                        baos.reset();
                        if (r == null) {
                            r = new byte[][]{buf};
                        } else {
                            // scan if new and add...
                            boolean found = false;
                            for (byte[] tmp : r) {
                                if (tmp.length == buf.length && Arrays.equals(tmp, buf)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                r = Arrays.copyOf(r, r.length + 1);
                                r[r.length - 1] = buf;
                            }
                        }
                        sIdx = 0;
                        eIdx = 0;
                    }
                } else {
                    baos.write(0xFF & b);
                }
            }
        }
        return r;
    }

    public static byte[][] scanUniqueSubsequences(byte[] starter, byte[] ender, byte... data) throws IOException {
        byte[][] r = null;
        int sLen = starter.length;
        int eLen = ender.length;
        int sIdx = 0;
        int eIdx = 0;

        ByteArrayOutputStream baos = null;
        int c = 0;
        for (byte b : data) {
            // check if sequence starter...
            if (sIdx < sLen) {
                if (starter[sIdx] == b) {
                    sIdx++;
                    if (sIdx == sLen) {
                        if (baos == null) {
                            baos = new ByteArrayOutputStream();
                        } else {
                            baos.reset();
                        }
                        baos.write(starter);
                    }
                } else {
                    sIdx = 0;
                }
            } else // check if byte closes sequence -> add new uniqe and reset
            if (sIdx == sLen) {
                if (eIdx < eLen) {
                    if (ender[eIdx] == b) {
                        eIdx++;
                    }
                    baos.write(0xFF & b);
                    if (eIdx == eLen) {
                        byte[] buf = baos.toByteArray();
                        baos.reset();
                        if (r == null) {
                            r = new byte[][]{buf};
                        } else {
                            // scan if new and add...
                            boolean found = false;
                            for (byte[] tmp : r) {
                                if (tmp.length == buf.length && Arrays.equals(tmp, buf)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                r = Arrays.copyOf(r, r.length + 1);
                                r[r.length - 1] = buf;
                            }
                        }
                        sIdx = 0;
                        eIdx = 0;
                    }
                } else {
                    baos.write(0xFF & b);
                }
            }
        }
        return r;
    }

    /**
     * Returns sets of detected subsequences marked with start/end byte
     * sequences in order priority (i.e. 1st in order is closeable if same
     * enders).
     *
     * Startend is ordered set of paired byte arrays [i][1][] - starter,
     * [i][1][] - ender.
     *
     * @param startend
     * @param data
     * @return
     * @throws IOException
     */
    public static byte[][][] scanUniqueSubsequences(byte[][][] startend, InputStream is) throws IOException {
        byte[][][] r = new byte[startend.length][][];
        int[] sLen = new int[startend.length]; // starter.length;
        int[] eLen = new int[startend.length]; // ender.length;
        int[] sIdx = new int[startend.length]; // 0;
        int[] eIdx = new int[startend.length]; // 0;

        for (int i = 0; i < sLen.length; i++) {
            sLen[i] = startend[i][0].length;
            eLen[i] = startend[i][1].length;
            sIdx[i] = 0;
            eIdx[i] = 0;
        }

        ByteArrayOutputStream[] baos = new ByteArrayOutputStream[startend.length];
        int c = 0;
        int v = 0;
        while ((v = is.read()) != -1) {
            byte b = (byte) v;
            // check if sequence starter...
            boolean itemFound = false;
            for (int i = 0; i < sLen.length; i++) {
                if (startend[i] == null) {
                    continue;
                }
                if (sIdx[i] < sLen[i]) {
                    if (startend[i][0][sIdx[i]] == b) {
                        sIdx[i]++;
                        if (sIdx[i] == sLen[i]) {
                            if (baos[i] == null) {
                                baos[i] = new ByteArrayOutputStream();
                            } else {
                                baos[i].reset();
                            }
                            baos[i].write(startend[i][0]);
                        }
                    } else {
                        sIdx[i] = 0;
                        eIdx[i] = 0;
                    }
                } else // check if byte closes sequence -> add new uniqe and reset
                if (sIdx[i] == sLen[i]) {
                    if (eIdx[i] < eLen[i]) {
                        if (startend[i][1][eIdx[i]] == b) {
                            eIdx[i]++;
                        }
                        baos[i].write(0xFF & b);
                        if (eIdx[i] == eLen[i]) {
                            byte[] buf = baos[i].toByteArray();
                            if (r[i] == null) {
                                r[i] = new byte[][]{buf};
                            } else {
                                // scan if new and add...
                                boolean found = false;
                                for (byte[] tmp : r[i]) {
                                    if (tmp.length == buf.length && Arrays.equals(tmp, buf)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    r[i] = Arrays.copyOf(r[i], r[i].length + 1);
                                    r[i][r[i].length - 1] = buf;
                                }
                            }
                            //baos[i].reset();
                            //sIdx[i] = 0;
                            //eIdx[i] = 0;
                            for (ByteArrayOutputStream ba : baos) {
                                if (ba != null) {
                                    ba.reset();
                                }
                            }
                            Arrays.fill(sIdx, 0);
                            Arrays.fill(eIdx, 0);
                            itemFound = true;
                        }
                    } else {
                        baos[i].write(0xFF & b);
                    }
                    if (itemFound) {
                        break;
                    }
                } else {
//                    if (baos[i] != null) {
//                        baos[i].reset();
//                    }
//                    sIdx[i] = 0;
//                    eIdx[i] = 0;
                }
            }
        }
        return r;
    }

    /**
     * Returns sets of detected subsequences marked with start/end byte
     * sequences in order priority (i.e. 1st in order is closeable if same
     * enders).
     *
     * Startend is ordered set of paired byte arrays [i][1][] - starter,
     * [i][1][] - ender.
     *
     * @param startend
     * @param data
     * @return
     * @throws IOException
     */
    public static byte[][][] scanUniqueSubsequences(byte[][][] startend, byte... data) throws IOException {
        byte[][][] r = new byte[startend.length][][];
        int[] sLen = new int[startend.length]; // starter.length;
        int[] eLen = new int[startend.length]; // ender.length;
        int[] sIdx = new int[startend.length]; // 0;
        int[] eIdx = new int[startend.length]; // 0;

        for (int i = 0; i < sLen.length; i++) {
            sLen[i] = startend[i][0].length;
            eLen[i] = startend[i][1].length;
            sIdx[i] = 0;
            eIdx[i] = 0;
        }

        ByteArrayOutputStream[] baos = new ByteArrayOutputStream[startend.length];
        int c = 0;
        for (byte b : data) {
            // check if sequence starter...
            boolean itemFound = false;
            for (int i = 0; i < sLen.length; i++) {
                if (startend[i] == null) {
                    continue;
                }
                if (sIdx[i] < sLen[i]) {
                    if (startend[i][0][sIdx[i]] == b) {
                        sIdx[i]++;
                        if (sIdx[i] == sLen[i]) {
                            if (baos[i] == null) {
                                baos[i] = new ByteArrayOutputStream();
                            } else {
                                baos[i].reset();
                            }
                            baos[i].write(startend[i][0]);
                        }
                    } else {
                        sIdx[i] = 0;
                        eIdx[i] = 0;
                    }
                } else // check if byte closes sequence -> add new uniqe and reset
                if (sIdx[i] == sLen[i]) {
                    if (eIdx[i] < eLen[i]) {
                        if (startend[i][1][eIdx[i]] == b) {
                            eIdx[i]++;
                        }
                        baos[i].write(0xFF & b);
                        if (eIdx[i] == eLen[i]) {
                            byte[] buf = baos[i].toByteArray();
                            if (r[i] == null) {
                                r[i] = new byte[][]{buf};
                            } else {
                                // scan if new and add...
                                boolean found = false;
                                for (byte[] tmp : r[i]) {
                                    if (tmp.length == buf.length && Arrays.equals(tmp, buf)) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    r[i] = Arrays.copyOf(r[i], r[i].length + 1);
                                    r[i][r[i].length - 1] = buf;
                                }
                            }
                            //baos[i].reset();
                            //sIdx[i] = 0;
                            //eIdx[i] = 0;
                            for (ByteArrayOutputStream ba : baos) {
                                if (ba != null) {
                                    ba.reset();
                                }
                            }
                            Arrays.fill(sIdx, 0);
                            Arrays.fill(eIdx, 0);
                            itemFound = true;
                        }
                    } else {
                        baos[i].write(0xFF & b);
                    }
                    if (itemFound) {
                        break;
                    }
                } else {
//                    if (baos[i] != null) {
//                        baos[i].reset();
//                    }
//                    sIdx[i] = 0;
//                    eIdx[i] = 0;
                }
            }
        }
        return r;
    }

    public static Long[] toObjectArray(long... v) {
        if (v != null) {
            Long[] r = new Long[v.length];
            for (int i = 0; i < v.length; i++) {
                r[i] = v[i];
            }
            return r;
        } else {
            return null;
        }
    }

    public static long[] fromObjectArray(Long... v) {
        if (v != null) {
            long[] r = new long[v.length];
            for (int i = 0; i < v.length; i++) {
                r[i] = v[i];
            }
            return r;
        } else {
            return null;
        }
    }

    /**
     * Expects waitCondition.needWait=false or timeout. Returns false if existed
     * on error or timeout;
     *
     * @param timeout
     * @param waitCondition
     * @return
     */
    public static boolean wait(long timeout, WaitCondition waitCondition) {
        if (waitCondition == null) {
            waitCondition = noWaitCondition;
        }
        long threshold = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < threshold && waitCondition.needWaiting()) {
            try {
                Thread.sleep(5);
            } catch (Throwable th) {
                return false;
            }
        }
        return !waitCondition.needWaiting();
    }

    /**
     * Builds stacktrace by generating exception and writing its output to
     * returned string.
     *
     * @param indent
     * @return
     */
    public static String stackTrace(int indent) {
        try (final StringWriter sw = new StringWriter()) {
            new Exception("").printStackTrace(new PrintWriter(sw));
            return sw.toString().indent(indent);
        } catch (IOException ioex) {
            return "";
        }
    }

    @FunctionalInterface
    public static interface WaitCondition {

        boolean needWaiting();
    }
    public static WaitCondition noWaitCondition = new WaitCondition() {
        public boolean needWaiting() {
            return false;
        }
    };

//    public static String[][] scanUniqueSubsequences(char[] starter, char[] ender, Reader rdr) throws IOException {
//    }
//
//    public static String[][] scanUniqueSubsequences(char[] starter, char[] ender, String data) throws IOException {
//    }
}
