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
package ssg.lib.common.buffers;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author 000ssg
 */
public class BufferTools {

    /**
     * Buffer usage state for general use. Useful to indicate missing data or
     * not enough space for data.
     */
    public static enum BUFFER_STATE {
        OK,
        OVERFLOW,
        UNDERFLOW
    }

    public static enum BUFFER_TYPE {
        none,
        byte_,
        char_,
        double_,
        float_,
        int_,
        long_,
        short_,
        unknown;

        public static BUFFER_TYPE typeOf(Buffer buf) {
            return (buf == null)
                    ? none
                    : (buf instanceof ByteBuffer)
                            ? byte_
                            : (buf instanceof CharBuffer)
                                    ? char_
                                    : (buf instanceof DoubleBuffer)
                                            ? double_
                                            : (buf instanceof FloatBuffer)
                                                    ? float_
                                                    : (buf instanceof IntBuffer)
                                                            ? int_
                                                            : (buf instanceof LongBuffer)
                                                                    ? long_
                                                                    : (buf instanceof ShortBuffer)
                                                                            ? short_
                                                                            : unknown;

        }
    }

    /**
     * True if has remaining in provided buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> boolean hasRemaining(Collection<T>... bufs) {
        if (bufs != null) {
            for (Collection<T> cbufs : bufs) {
                if (cbufs != null) {
                    for (T buf : cbufs) {
                        if (buf != null && buf.hasRemaining()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * True if has remaining in provided buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> boolean hasRemaining(T... bufs) {
        if (bufs != null) {
            for (T buf : bufs) {
                if (buf != null && buf.hasRemaining()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns count of elements remaining in provided buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long getRemaining(Collection<T>... bufs) {
        long c = 0;
        if (bufs != null) {
            for (Collection<T> cbufs : bufs) {
                if (cbufs != null) {
                    for (T buf : cbufs) {
                        if (buf != null) {
                            c += buf.remaining();
                        }
                    }
                }
            }
        }
        return c;
    }

    /**
     * Returns count of elements remaining in provided buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long getRemaining(T... bufs) {
        long c = 0;
        if (bufs != null) {
            for (T buf : bufs) {
                if (buf != null) {
                    c += buf.remaining();
                }
            }
        }
        return c;
    }

    /**
     * Returns count of elements remaining in provided buffers and marks them.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long mark(Collection<T>... bufs) {
        long c = 0;
        if (bufs != null) {
            for (Collection<T> cbufs : bufs) {
                if (cbufs != null) {
                    for (T buf : cbufs) {
                        if (buf != null) {
                            c += buf.remaining();
                            buf.mark();
                        }
                    }
                }
            }
        }
        return c;
    }

    /**
     * Returns count of elements remaining in provided buffers and marks them.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long mark(T... bufs) {
        long c = 0;
        if (bufs != null) {
            for (T buf : bufs) {
                if (buf != null) {
                    c += buf.remaining();
                    buf.mark();
                }
            }
        }
        return c;
    }

    /**
     * Returns count of elements remaining in provided buffers after reset.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long reset(Collection<T>... bufs) {
        long c = 0;
        if (bufs != null) {
            for (Collection<T> cbufs : bufs) {
                if (cbufs != null) {
                    for (T buf : cbufs) {
                        if (buf != null) {
                            buf.reset();
                            c += buf.remaining();
                        }
                    }
                }
            }
        }
        return c;
    }

    /**
     * Returns count of elements remaining in provided buffers after reset.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long reset(T... bufs) {
        long c = 0;
        if (bufs != null) {
            for (T buf : bufs) {
                if (buf != null) {
                    buf.reset();
                    c += buf.remaining();
                }
            }
        }
        return c;
    }

    /**
     * Returns list of only non-empty non-null buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> List<T> getNonEmpty(Collection<T>... bufs) {
        List<T> r = new ArrayList<>();
        if (getRemaining((Collection<T>[]) bufs) > 0) {
            for (Collection<T> cbufs : bufs) {
                if (cbufs != null && !cbufs.isEmpty()) {
                    for (T buf : cbufs) {
                        if (buf != null && buf.remaining() > 0) {
                            r.add(buf);
                        }
                    }
                }
            }
        }
        return r;
    }

    public static byte[] toBytes(boolean copyOnly, ByteBuffer... bufs) {
        long c = getRemaining(bufs);
        byte[] r = new byte[(int) c];
        ByteBuffer rbb = ByteBuffer.wrap(r);
        for (ByteBuffer buf : bufs) {
            if (buf != null && buf.remaining() > 0) {
                if (copyOnly) {
                    buf.mark();
                }
                rbb.put(buf);
                if (copyOnly) {
                    buf.reset();
                }
            }
        }
        return r;
    }

    public static ByteBuffer[] asByteBuffersArray(Collection<ByteBuffer>... bufs) {
        ByteBuffer[] r = null;
        int c = 0;
        if (bufs != null) {
            for (Collection<ByteBuffer> bbs : bufs) {
                if (bbs == null) {
                    continue;
                }
                for (ByteBuffer bb : bbs) {
                    if (bb != null && bb.hasRemaining()) {
                        c++;
                    }
                }
            }
        }

        r = new ByteBuffer[c];
        c = 0;
        if (bufs != null) {
            for (Collection<ByteBuffer> bbs : bufs) {
                if (bbs == null) {
                    continue;
                }
                for (ByteBuffer bb : bbs) {
                    if (bb != null && bb.hasRemaining()) {
                        r[c++] = bb;
                    }
                }
            }
        }

        return r;
    }

    public static byte[] toBytes(boolean copyOnly, Collection<ByteBuffer>... bufs) {
        long c = getRemaining(bufs);
        byte[] r = new byte[(int) c];
        ByteBuffer rbb = ByteBuffer.wrap(r);
        if (bufs != null) {
            for (Collection<ByteBuffer> cbufs : bufs) {
                if (cbufs != null && !cbufs.isEmpty()) {
                    for (ByteBuffer buf : cbufs) {
                        if (buf != null && buf.remaining() > 0) {
                            if (copyOnly) {
                                buf.mark();
                            }
                            rbb.put(buf);
                            if (copyOnly) {
                                buf.reset();
                            }
                        }
                    }
                }
            }
        }
        return r;
    }

    public static ByteBuffer fetch(int size, Collection<ByteBuffer>... bbs) {
        ByteBuffer bb = ByteBuffer.allocate((int) Math.min(size, getRemaining(bbs)));
        for (Collection<ByteBuffer> bs : bbs) {
            if (bs != null) {
                for (ByteBuffer bi : bs) {
                    if (bi != null) {
                        while (bi.hasRemaining()) {
                            bb.put(bi.get());
                            if (!bb.hasRemaining()) {
                                break;
                            }
                        }
                    }
                }
            }
            if (!bb.hasRemaining()) {
                break;
            }
        }
        ((Buffer) bb).flip();
        return bb;
    }

    public static ByteBuffer fetchAndRemove(int size, Collection<ByteBuffer>... bbs) {
        ByteBuffer bb = ByteBuffer.allocate((int) Math.min(size, getRemaining(bbs)));
        for (Collection<ByteBuffer> bs : bbs) {
            if (bs != null) {
                for (ByteBuffer bi : bs.toArray(new ByteBuffer[bs.size()])) {
                    if (bi != null) {
                        while (bi.hasRemaining()) {
                            bb.put(bi.get());
                            if (!bb.hasRemaining()) {
                                break;
                            }
                        }
                        if (!bi.hasRemaining()) {
                            bs.remove(bi);
                        }
                    }
                }
            }
            if (!bb.hasRemaining()) {
                break;
            }
        }
        ((Buffer) bb).flip();
        return bb;
    }

    /**
     * Non-destructive (after operation source buffers stay untouched...) read
     * from buffers into target.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> T probe(T target, Collection<T>... bufs) {
        long c = getRemaining(bufs);
        if (c > 0 && target.hasRemaining()) {
            BUFFER_TYPE bt = BUFFER_TYPE.typeOf(target);
            if (bt == BUFFER_TYPE.none || bt == BUFFER_TYPE.unknown) {
                return null;
            }
            for (Collection<T> bbs : bufs) {
                if (bbs == null || bbs.isEmpty()) {
                    continue;
                }
                for (T t : bbs) {
                    t.mark();
                    try {
                        while (target.hasRemaining() && t.hasRemaining()) {
                            switch (bt) {
                                case byte_:
                                    ((ByteBuffer) target).put(((ByteBuffer) t).get());
                                    break;
                                case char_:
                                    ((CharBuffer) target).put(((CharBuffer) t).get());
                                    break;
                                case double_:
                                    ((DoubleBuffer) target).put(((DoubleBuffer) t).get());
                                    break;
                                case float_:
                                    ((FloatBuffer) target).put(((FloatBuffer) t).get());
                                    break;
                                case int_:
                                    ((IntBuffer) target).put(((IntBuffer) t).get());
                                    break;
                                case long_:
                                    ((LongBuffer) target).put(((LongBuffer) t).get());
                                    break;
                                case short_:
                                    ((ShortBuffer) target).put(((ShortBuffer) t).get());
                                    break;
                                default:
                                    throw new RuntimeException("Never get here.");
                            }
                        }
                    } finally {
                        t.reset();
                    }
                    if (!target.hasRemaining()) {
                        break;
                    }
                }
                if (!target.hasRemaining()) {
                    break;
                }
            }
        }
        ((Buffer) target).flip();
        return target;
    }

    /**
     * Non-destructive fetch of up to maxLength bytes until 1st appearance of
     * eod (inclusive).
     *
     * @param target
     * @param maxLength
     * @param eod
     * @param bufs
     * @return
     */
    public static ByteBuffer probe(ByteBuffer target, int maxLength, byte eod, Collection<ByteBuffer>... bufs) {
        if (target == null) {
            target = ByteBuffer.allocate(maxLength);
        }
        if (bufs != null) {
            boolean done = false;
            for (Collection<ByteBuffer> bbs : bufs) {
                if (bbs != null && !bbs.isEmpty()) {
                    for (ByteBuffer bb : bbs) {
                        if (bb != null && bb.hasRemaining()) {
                            bb.mark();
                            try {
                                while (target.hasRemaining() && bb.hasRemaining()) {
                                    byte b = bb.get();
                                    target.put(b);
                                    if (b == eod) {
                                        done = true;
                                        break;
                                    }
                                }
                            } finally {
                                bb.reset();
                            }
                        }
                        if (done || !target.hasRemaining()) {
                            break;
                        }
                    }
                }
                if (done || !target.hasRemaining()) {
                    break;
                }
            }
        }
        ((Buffer) target).flip();
        return target;
    }

    /**
     * Returns list of only non-empty non-null buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> List<T> getNonEmpty(T... bufs) {
        List<T> r = new ArrayList<>();
        if (getRemaining(bufs) > 0) {
            for (T buf : bufs) {
                if (buf != null && buf.remaining() > 0) {
                    r.add(buf);
                }
            }
        }
        return r;
    }

    /**
     * Returns list of only non-empty non-null buffers.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> T[] getNonEmpties(T... bufs) {
        if (getRemaining(bufs) > 0) {
            int off = 0;
            for (int i = 0; i < bufs.length; i++) {
                T buf = bufs[i];
                if (buf != null && buf.remaining() > 0) {
                    if (i > off) {
                        bufs[off++] = bufs[i];
                    } else {
                        off++;
                    }
                }
            }
            return Arrays.copyOf(bufs, off);
        } else if (bufs != null) {
            return Arrays.copyOf(bufs, 0);
        } else {
            return null;
        }

    }

    /**
     * Returns non-null items or null if none.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T> T[] getNonNulls(T... bufs) {
        int c = 0;
        if (bufs != null) {
            for (T m : bufs) {
                if (m != null) {
                    c++;
                }
            }
        }
        if (c == 0) {
            return null;
        } else if (c != bufs.length) {
            T[] mms = Arrays.copyOf(bufs, c);
            int idx = 0;
            for (int i = 0; i < bufs.length; i++) {
                if (bufs[i] != null) {
                    mms[idx++] = bufs[i];
                }
            }
        }
        return bufs;
    }

    /**
     * Moves data from "from" to "to" ignoring nulls/empties. Clears from.
     *
     * @param to
     * @param from
     * @return
     */
    public static <T extends Buffer> long moveBuffersTo(Collection<T> to, Collection<T>... from) {
        long c = 0;
        if (from != null) {
            List<T> tmp = new ArrayList<>();
            for (Collection<T> bbs : from) {
                if (bbs == null || bbs.isEmpty()) {
                    continue;
                }

                tmp.addAll(bbs);
                boolean needCopy = false;
                try {
                    bbs.clear();
                } catch (Throwable th) {
                    //th.printStackTrace();
                    needCopy = true;
                }

                for (T bb : tmp) {
                    if (bb != null && bb.hasRemaining()) {
                        c += bb.remaining();
                        if (needCopy) {
                            T tmpBB = (T) BufferTools.createBuffer(bb, bb.remaining(), true);
                            try {
                                BufferTools.copy(bb, tmpBB);
                                ((Buffer) tmpBB).flip();
                                to.add(tmpBB);
                            } catch (IOException ioex) {
                            }
                        } else {
                            to.add(bb);
                        }
                    }
                }
                tmp.clear();
            }
        }
        return c;
    }

    /**
     * Removes null or no-data (no remaining items) buffers from provides
     * collections.
     *
     * Returns number of found items.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> long removeEmpties(Collection<T>... bufs) {
        long l = 0;
        if (bufs != null) {
            for (Collection<T> cbufs : bufs) {
                //synchronized (cbufs) {
                Iterator<T> it = cbufs.iterator();
                while (it.hasNext()) {
                    T t = it.next();
                    if (t == null || !t.hasRemaining()) {
                        it.remove();
                    } else {
                        l += t.remaining();
                    }
                }
                //}
            }
        }
        return l;
    }

    public static <T extends Buffer> void clearBufffers(T... bufs) {
        if (bufs != null) {
            for (T bb : bufs) {
                if (bb != null) {
                    ((Buffer) bb).clear();
                }
            }
        }

    }

    public static <T extends Buffer> void clearBufffers(Collection<T>... bufs) {
        if (bufs != null) {
            for (Collection<T> bbs : bufs) {
                if (bbs != null) {
                    for (T bb : bbs) {
                        if (bb != null) {
                            ((Buffer) bb).clear();
                        }
                    }
                }
            }
        }
    }

    public static long readTo(List<ByteBuffer> bbs, byte[] buf) {
        return readTo(bbs, buf, 0, buf.length);
    }

    public static long readTo(List<ByteBuffer> bbs, byte[] buf, int off, int len) {
        long l = 0;

        if (bbs != null && buf != null && len > 0) {
            for (ByteBuffer bb : bbs) {
                if (!bb.hasRemaining()) {
                    continue;
                }
                int c = readTo(bb, buf, off, len);
                if (c > 0) {
                    l += c;
                    off += c;
                    len -= c;
                }
                if (len == 0) {
                    break;
                }
            }
        }

        return l;
    }

    public static long readTo(ByteBuffer[] bbs, byte[] buf) {
        return readTo(bbs, buf, 0, buf.length);
    }

    public static long readTo(ByteBuffer[] bbs, byte[] buf, int off, int len) {
        long l = 0;

        if (bbs != null && buf != null && len > 0) {
            for (ByteBuffer bb : bbs) {
                if (bb == null || !bb.hasRemaining()) {
                    continue;
                }
                int c = readTo(bb, buf, off, len);
                if (c > 0) {
                    l += c;
                    off += c;
                    len -= c;
                }
                if (len == 0) {
                    break;
                }
            }
        }

        return l;
    }

    public static int readTo(ByteBuffer bb, byte[] buf, int off, int len) {
        int c = 0;
        if (bb != null && bb.hasRemaining() && len > 0) {
            while (bb.hasRemaining() && len > 0) {
                byte b = bb.get();
                buf[off++] = b;
                len--;
                c++;
            }
        }
        return c;
    }

    public static String toText(String encoding, Collection<ByteBuffer>... bufs) {
        if (encoding == null) {
            encoding = "UTF-8";
        }
        StringBuilder sb = new StringBuilder();
        Collection<ByteBuffer> bbs = getNonEmpty(bufs);
        if (bbs != null) {
            byte[] buf = new byte[1024 * 64];
            for (ByteBuffer bb : bbs) {
                int c = bb.remaining();
                bb.duplicate().get(buf, 0, c);
                try {
                    sb.append(new String(buf, 0, c, encoding));
                } catch (Throwable th) {
                    //th.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    public static String toText(String encoding, ByteBuffer... bufs) {
        if (encoding == null) {
            encoding = "UTF-8";
        }
        StringBuilder sb = new StringBuilder();
        Collection<ByteBuffer> bbs = getNonEmpty(bufs);
        if (bbs != null) {
            byte[] buf = new byte[1024 * 64];
            for (ByteBuffer bb : bbs) {
                int c = bb.remaining();
                bb.duplicate().get(buf, 0, c);
                try {
                    sb.append(new String(buf, 0, c, encoding));
                } catch (Throwable th) {
                    //th.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    public static String toText(String encoding, byte[]  
        ... bufs) {
        if (encoding == null) {
            encoding = "UTF-8";
        }
        byte[] buf = merge(bufs);
        try {
            return new String(buf, 0, buf.length, encoding);
        } catch (Throwable th) {
            //th.printStackTrace();
        }
        return null;
    }

    public static String toText(Collection<CharBuffer>... bufs) {
        StringBuilder sb = new StringBuilder();
        Collection<CharBuffer> bbs = getNonEmpty(bufs);
        if (bbs != null) {
            for (CharBuffer bb : bbs) {
                if (bb != null && bb.hasRemaining()) {
                    sb.append(bb.toString());
                }
            }
        }
        return sb.toString();
    }

    public static String toText(CharBuffer... bufs) {
        StringBuilder sb = new StringBuilder();
        Collection<CharBuffer> bbs = getNonEmpty(bufs);
        if (bbs != null) {
            for (CharBuffer bb : bbs) {
                if (bb != null && bb.hasRemaining()) {
                    sb.append(bb.toString());
                }
            }
        }
        return sb.toString();
    }

    public static int sizeOf(Collection<byte[]>... bufs) {
        int c = 0;
        if (bufs != null) {
            for (Collection<byte[]> bbs : bufs) {
                for (byte[] bb : bbs) {
                    if (bb != null) {
                        c += bb.length;
                    }
                }
            }
        }
        return c;
    }

    public static int sizeOf(byte[]  
        ... bufs) {
        int c = 0;
        if (bufs != null) {
            for (byte[] bb : bufs) {
                if (bb != null) {
                    c += bb.length;
                }
            }
        }
        return c;
    }

    public static byte[] merge(Collection<byte[]>... bufs) {
        int size = sizeOf(bufs);
        int off = 0;
        byte[] buf = new byte[size];
        for (Collection<byte[]> bbs : bufs) {
            for (byte[] bb : bbs) {
                if (bb != null && bb.length > 0) {
                    System.arraycopy(bb, 0, buf, off, bb.length);
                    off += bb.length;
                }
            }
        }
        return buf;
    }

    public static byte[] merge(byte[]  
        ... bufs) {
        if (bufs != null && bufs.length == 1) {
            return bufs[0];
        }
        int size = sizeOf(bufs);
        int off = 0;
        byte[] buf = new byte[size];
        for (byte[] bb : bufs) {
            if (bb != null && bb.length > 0) {
                System.arraycopy(bb, 0, buf, off, bb.length);
                off += bb.length;
            }
        }
        return buf;
    }

    public static String dump(Object... buf) {
        StringBuilder sb = new StringBuilder();
        if (buf != null) {
            if (buf.length == 1) {
                Object obj = buf[0];
                if (obj == null) {
                    sb.append("null");
                } else if (obj instanceof byte[]) {
                    sb.append(dump((byte[]) obj));
                } else if (obj instanceof Collection) {
                    Collection c = (Collection) obj;
                    sb.append(obj.getClass().getSimpleName() + "[" + c.size() + "]\n");
                    for (Object o : c) {
                        sb.append("\n  " + dump(o).replace("\n", "\n  "));
                    }
                } else if (obj.getClass().isArray()) {
                    sb.append(obj.getClass().getComponentType().getName() + "[" + Array.getLength(obj) + "]\n");
                    for (int i = 0; i < Array.getLength(obj); i++) {
                        sb.append("\n  " + dump(Array.get(obj, i)).replace("\n", "\n  "));
                    }
                } else if (obj instanceof ByteBuffer) {
                    ByteBuffer bb = (ByteBuffer) obj;
                    byte[] bbuf = new byte[bb.remaining()];
                    bb.duplicate().get(bbuf);
                    sb.append(obj.toString() + " : " + dump(bbuf));
                } else {
                    sb.append(("" + buf[0]).replace("\n", "\n  "));
                }
            } else {
                sb.append(buf.getClass().getComponentType().getName() + "[" + buf.length + "]\n");
                for (int i = 0; i < buf.length; i++) {
                    sb.append("\n  " + dump(buf[i]).replace("\n", "\n  "));
                }
            }
        }
        return sb.toString();
    }

    public static String dump(byte... buf) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sbl = new StringBuilder();
        if (buf != null) {
            sb.append("byte[" + buf.length + "]\n");

            for (int i = 0; i < buf.length; i++) {
                if (i > 0 && i % 16 == 0) {
                    sb.append(" | ");
                    sb.append(sbl.toString());
                    sbl.delete(0, sbl.length());
                    sb.append("\n");
                }
                sb.append(' ');
                if (buf[i] >= ' ') {
                    sbl.append((char) buf[i]);
                } else {
                    sbl.append('.');
                }
                if (buf[i] >= 0 && buf[i] <= 0xF) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(0xFF & buf[i]));
            }
            int oo = (buf.length) % 16;
            if (oo > 0) {
                oo = 16 - oo;
            }
            while (oo > 0) {
                sb.append("   ");
                oo--;
            }
            sb.append(" | ");
            sb.append(sbl.toString());
            sbl.delete(0, sbl.length());
        }
        return sb.toString();
    }

    public static String dummpErrorMessages(Throwable th) {
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        while (th != null) {
            sb.append(prefix);
            sb.append(th.toString());
            if (prefix.isEmpty()) {
                prefix += "\n";
            }
            prefix += "  ";
            th = th.getCause();
        }
        return sb.toString();
    }

    public static List<ByteBuffer> toByteBuffers(InputStream is) throws IOException {
        List<ByteBuffer> r = new ArrayList<ByteBuffer>();
        byte[] buf = new byte[1024];
        int c = 0;
        while ((c = is.read(buf)) != -1) {
            if (c == buf.length) {
                r.add(ByteBuffer.wrap(buf));
                buf = new byte[1024];
            } else {
                r.add(ByteBuffer.wrap(Arrays.copyOf(buf, c)));
            }
        }
        return r;
    }

    public static List<CharBuffer> toCharBuffers(Reader rdr) throws IOException {
        List<CharBuffer> r = new ArrayList<CharBuffer>();
        char[] buf = new char[1024];
        int c = 0;
        while ((c = rdr.read(buf)) != -1) {
            if (c == buf.length) {
                r.add(CharBuffer.wrap(buf));
                buf = new char[1024];
            } else {
                r.add(CharBuffer.wrap(Arrays.copyOf(buf, c)));
            }
        }
        return r;
    }

    /**
     * Creates buffer similar to sample possibly in "direct" mode (for
     * ByteBuffer type only)
     *
     * @param <T>
     * @param sample
     * @param size
     * @param direct
     * @return
     */
    public static <T extends Buffer> T createBuffer(T sample, int size, boolean direct) {
        Buffer r = null;
        if (sample == null) {
        } else if (sample instanceof ByteBuffer) {
            r = (direct) ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
        } else if (sample instanceof ShortBuffer) {
            r = ShortBuffer.allocate(size);
        } else if (sample instanceof IntBuffer) {
            r = IntBuffer.allocate(size);
        } else if (sample instanceof LongBuffer) {
            r = LongBuffer.allocate(size);
        } else if (sample instanceof CharBuffer) {
            r = CharBuffer.allocate(size);
        } else if (sample instanceof FloatBuffer) {
            r = FloatBuffer.allocate(size);
        } else if (sample instanceof DoubleBuffer) {
            r = DoubleBuffer.allocate(size);
        } else {
            //throw new IOException("Unsupported Buffer type: " + sample);
        }
        return (T) r;
    }

    /**
     * Returns copy of input buffers as data arranged in blocks of size +
     * reminder ( if any).
     *
     * @param <T>
     * @param size
     * @param bufs
     * @return
     */
    public static <T extends Buffer> List<T> aggregate(int size, boolean direct, Collection<T>... bufs) throws IOException {
        List<T> r = new ArrayList<>();

        long c = getRemaining(bufs);
        int blocks = (size > 0) ? (int) (c / size) : 0;
        int reminder = (int) (c - blocks * size);

        T bb = null;
        if (bufs != null) {
            for (Collection<T> bs : bufs) {
                if (bs != null) {
                    for (T b : bs) {
                        if (b != null && b.hasRemaining()) {
                            while (b.hasRemaining()) {
                                if (bb == null || !bb.hasRemaining()) {
                                    if (bb != null) {
                                        ((Buffer) bb).flip();
                                    }
                                    int sz = 0;
                                    if (blocks > 0) {
                                        sz = size;
                                        blocks--;
                                    } else if (reminder > 0) {
                                        sz = reminder;
                                        reminder = 0;
                                    }
                                    if (sz == 0) {
                                        break;
                                    } else {
                                        bb = createBuffer(b, sz, direct);
                                        r.add(bb);
                                    }
                                }
                                copy(b, bb);
                            }
                        }
                    }
                }
                if (blocks == 0 && reminder == 0 && bb != null && !bb.hasRemaining()) {
                    break;
                }
            }
        }
        if (bb != null && !bb.hasRemaining()) {
            ((Buffer) bb).flip();
        }
        return r;
    }

    /**
     * Returns copy of input buffers as data arranged in blocks of size +
     * reminder ( if any).
     *
     * @param <T>
     * @param size
     * @param bufs
     * @return
     */
    public static <T extends Buffer> List<T> aggregate(int size, boolean direct, T... bufs) throws IOException {
        List<T> r = new ArrayList<>();

        long c = getRemaining(bufs);
        int blocks = (int) (c / size);
        int reminder = (int) (c - blocks * size);

        T bb = null;
        if (bufs != null) {
            for (T b : bufs) {
                if (b != null && b.hasRemaining()) {
                    while (b.hasRemaining()) {
                        if (bb == null || !bb.hasRemaining()) {
                            if (bb != null) {
                                ((Buffer) bb).flip();
                            }
                            int sz = 0;
                            if (blocks > 0) {
                                sz = size;
                                blocks--;
                            } else if (reminder > 0) {
                                sz = reminder;
                                reminder = 0;
                            }
                            if (sz == 0) {
                                break;
                            } else {
                                bb = createBuffer(b, sz, direct);
                                r.add(bb);
                            }
                        }
                        copy(b, bb);
                    }
                }
                if (blocks == 0 && reminder == 0 && bb != null && !bb.hasRemaining()) {
                    break;
                }
            }
        }
        if (bb != null && !bb.hasRemaining()) {
            ((Buffer) bb).flip();
        }
        return r;
    }

    /**
     * Copies from to as much items as possible.
     *
     * @param <T>
     * @param from
     * @param to
     * @return
     * @throws IOException
     */
    public static <T extends Buffer> T copy(T from, T to) throws IOException {
        if (from == null || !from.hasRemaining() || !to.hasRemaining()) {
            return to;
        } else if (from instanceof ByteBuffer) {
            ByteBuffer f = (ByteBuffer) from;
            ByteBuffer t = (ByteBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else if (from instanceof ShortBuffer) {
            ShortBuffer f = (ShortBuffer) from;
            ShortBuffer t = (ShortBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else if (from instanceof IntBuffer) {
            IntBuffer f = (IntBuffer) from;
            IntBuffer t = (IntBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else if (from instanceof LongBuffer) {
            LongBuffer f = (LongBuffer) from;
            LongBuffer t = (LongBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else if (from instanceof CharBuffer) {
            CharBuffer f = (CharBuffer) from;
            CharBuffer t = (CharBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else if (from instanceof FloatBuffer) {
            FloatBuffer f = (FloatBuffer) from;
            FloatBuffer t = (FloatBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else if (from instanceof DoubleBuffer) {
            DoubleBuffer f = (DoubleBuffer) from;
            DoubleBuffer t = (DoubleBuffer) to;
            while (f.hasRemaining() && t.hasRemaining()) {
                t.put(f.get());
            }
        } else {
            throw new IOException("Unsupported Buffer type: " + from);
        }
        return to;
    }

    /**
     * Returns 1st non-null buffer or null if none.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> T firstNotNull(Collection<T>... bufs) {
        if (bufs != null) {
            for (Collection<T> bs : bufs) {
                if (bs != null) {
                    for (T b : bs) {
                        if (b != null) {
                            return b;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns 1st non-null buffer or null if none.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> T firstNotNull(T... bufs) {
        if (bufs != null) {
            for (T b : bufs) {
                if (b != null) {
                    return b;
                }
            }
        }
        return null;
    }

    /**
     * Returns 1st non-empty buffer or null if none.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> T firstNonEmpty(Collection<T>... bufs) {
        if (bufs != null) {
            for (Collection<T> bs : bufs) {
                if (bs != null) {
                    for (T b : bs) {
                        if (b != null && b.hasRemaining()) {
                            return b;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns 1st non-empty buffer or null if none.
     *
     * @param <T>
     * @param bufs
     * @return
     */
    public static <T extends Buffer> T firstNonEmpty(T... bufs) {
        if (bufs != null) {
            for (T b : bufs) {
                if (b != null && b.hasRemaining()) {
                    return b;
                }
            }
        }
        return null;
    }

    public static <T extends Buffer> List<T> toList(boolean skipEmpties, Collection<T>... bufs) {
        List<T> r = new ArrayList<>();
        if (bufs != null) {
            for (Collection<T> bbs : bufs) {
                if (bbs != null) {
                    for (T bb : bbs) {
                        if (bb != null) {
                            if (!skipEmpties || bb.hasRemaining()) {
                                r.add(bb);
                            }
                        }
                    }
                }
            }
        }
        return r;
    }

    public static <T extends Buffer> List<T> toList(boolean skipEmpties, T... bufs) {
        List<T> r = new ArrayList<>();
        if (bufs != null) {
            for (T bb : bufs) {
                if (bb != null) {
                    if (!skipEmpties || bb.hasRemaining()) {
                        r.add(bb);
                    }
                }
            }
        }
        return r;
    }

    public static <T extends Buffer> List<T> toList(boolean skipEmpties, T[] bufs, int off, int len) {
        List<T> r = new ArrayList<>();
        if (bufs != null && off >= 0 && off < bufs.length) {
            len += off;
            for (int i = off; i < len; i++) {
                T bb = bufs[i];
                if (bb != null) {
                    if (!skipEmpties || bb.hasRemaining()) {
                        r.add(bb);
                    }
                }
            }
        }
        return r;
    }
}
