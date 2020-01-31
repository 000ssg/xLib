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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import ssg.lib.common.Replacement;
import ssg.lib.common.Replacement.MATCH;
import static ssg.lib.common.Replacement.MATCH.exact;
import static ssg.lib.common.Replacement.MATCH.none;
import static ssg.lib.common.Replacement.MATCH.partial;
import ssg.lib.common.Replacement.Replacements;

/**
 *
 * @author 000ssg
 */
public class ByteBufferPipeReplacement extends ByteBufferPipe {

    public static boolean GDEBUG = false;
    public boolean DEBUG = GDEBUG;

    Replacement replace;
    List<byte[]> processed = new ArrayList<byte[]>() {
        @Override
        public boolean add(byte[] e) {
            if (DEBUG) {
                System.out.println(System.identityHashCode(ByteBufferPipeReplacement.this) + " ADD[" + e.length + "]:\n  |" + new String(e).replace("\n", "\n  |"));
            }
            return super.add(e);
        }
    };
    int ppos = 0;
    ByteBuffer fetch;
    byte[] preFetch;
    long fetched;

    public ByteBufferPipeReplacement(String src, String dst) {
        initReplacement(new Replacement(src, dst));
    }

    public ByteBufferPipeReplacement(Replacements replace) {
        initReplacement(replace);
    }

    public ByteBufferPipeReplacement(Replacement... replace) {
        initReplacement(replace);
    }

    public ByteBufferPipeReplacement(BufferPipe<ByteBuffer> nested, String src, String dst) {
        super(nested);
        initReplacement(new Replacement(src, dst));
    }

    public ByteBufferPipeReplacement(BufferPipe<ByteBuffer> nested, Replacement... replace) {
        super(nested);
        initReplacement(replace);
    }

    void initReplacement(Replacement... replace) {
        if (replace == null || replace.length == 0) {
            return;
        }
        this.replace = (replace.length > 1) ? new Replacements(replace) : replace[0];
        int rsz = 0;
        for (Replacement r : replace) {
            r.setAutoReset(true);
            int[] ri = r.getSizes();
            if (ri != null) {
                rsz = Math.max(Math.max(ri[0], ri[1]), rsz);
            }
        }
        fetch = ByteBuffer.allocate(Math.max(rsz + 1024, 1024 * 5));
    }

    @Override
    public boolean isClosed() {
        if (super.isClosed() && processed.isEmpty()) {
            try {
                // check if no final data can be obtined...
                if (fetch() == -1) {
                    return true;
                }
            } catch (Throwable th) {
            }
            return false;
        } else {
            return false;
        }
    }

    int fetch() throws IOException {
        int result = 0;
        if (!fetch.hasRemaining() || fetched == 0) {
            ((Buffer) fetch).clear();
            int c = super.read(fetch);
            if (c > 0) {
                fetched += c;
                ((Buffer) fetch).flip();
            } else {
                if (preFetch != null) {
                    processed.add(preFetch);
                    c = preFetch.length;
                    preFetch = null;
                }
                ((Buffer) fetch).flip();
                return c;
            }
        }

        MATCH lastM = replace.getMatchState();
        ByteBuffer bb = ByteBuffer.allocate(fetch.remaining());// fetch.limit());
        while (fetch.hasRemaining()) {
            byte b = fetch.get();
            result++;
            int lastML = replace.getMatchLength();
            MATCH cm = replace.next(b);
//            if (DEBUG) {
//                System.out.println("\t" + b + "\t" + ((char) b) + "\t" + cm + "\t" + replace.toString().replace("\n", "\\n"));
//            }
            switch (cm) {
                case exact:
                    int[] sz = replace.getSizes();
                    if (bb.position() > sz[0] || preFetch != null && bb.position() + preFetch.length > sz[0]) {
                        //
                        ((Buffer) bb).flip();
                        int c = (preFetch != null) ? preFetch.length + bb.remaining() - sz[0] : bb.remaining() - sz[0];
                        byte[] tmp = new byte[c];
                        if (preFetch != null) {
                            for (int i = 0; i < Math.min(preFetch.length, tmp.length); i++) {
                                tmp[i] = preFetch[i];
                            }
                            if (preFetch.length < tmp.length) {
                                for (int i = preFetch.length; i < tmp.length; i++) {
                                    tmp[i] = bb.get();
                                }
                            }
                        } else {
                            for (int i = 0; i < tmp.length; i++) {
                                tmp[i] = bb.get();
                            }
                        }
                        processed.add(tmp);
                        preFetch = null;
                    } else if (preFetch != null) {
                        // nothing to keep
                        preFetch = null;
                    }
                    if (sz[1] > 0) {
                        if (DEBUG) {
                            System.out.println(System.identityHashCode(this) + " RPL[" + sz[0] + "] " + new String(replace.getSrc()));
                        }
                        processed.add(Arrays.copyOf(replace.getTrg(), sz[1]));
                    }
                    replace.reset();
                    ((Buffer)bb).clear();
                    break;
                case partial:
                    if (replace.getMatchLength() - lastML != 1) {
                        int a = 0;
                    }
                    if (MATCH.none == lastM && bb.position() > 0 || replace.getMatchLength() - lastML != 1) {
                        // add to processed pre-partial bytes
                        if (preFetch != null) {
                            processed.add(preFetch);
                            preFetch = null;
                        }
                        ((Buffer) bb).flip();
                        byte[] tmp = new byte[bb.remaining()];
                        for (int i = 0; i < tmp.length; i++) {
                            tmp[i] = bb.get();
                        }
                        processed.add(tmp);
                        bb.compact();
                    }
                    bb.put(b);
                    break;
                case none:
                    if (lastML > 0) {
                        replace.reset();
                    }
                    bb.put(b);
                    break;
            }
            lastM = cm;
        }

        if (MATCH.partial == replace.getMatchState() || MATCH.none == replace.getMatchState()) {
            ((Buffer) bb).flip();
            int c = bb.remaining();
            if (MATCH.none == replace.getMatchState()) {
                byte[] tmp = new byte[c];
                for (int i = 0; i < tmp.length; i++) {
                    tmp[i] = bb.get();
                }
                processed.add(tmp);
            } else {
                if (preFetch != null) {
                    int off = preFetch.length;
                    preFetch = Arrays.copyOf(preFetch, preFetch.length + c);
                    //for (int i = 0; i < preFetch.length; i++) {
                    for (int i = 0; i < c; i++) {
                        preFetch[i + off] = bb.get();
                    }
                } else {
                    preFetch = new byte[c];
                    for (int i = 0; i < preFetch.length; i++) {
                        preFetch[i] = bb.get();
                    }
                }
            }
        }

        return result;
    }

    public long getFetchedSize() {
        return fetched;
    }

    public long getProcessedSize() {
        long l = 0;
        for (byte[] bb : processed) {
            l += bb.length;
        }
        return l;
    }

    public int read(ByteBuffer bb) throws IOException {
        if (processed.isEmpty()) {
            //                if (closed) {
            //                    if (rbufpos == rbufmax && added.isEmpty()) {
            //                        return -1;
            //                    }
            //                }
            try {
                int c = fetch();
                if (c == -1) {
                    return -1;
                }
            } catch (Throwable th) {
                if (th instanceof IOException) {
                    throw (IOException) th;
                } else {
                    throw new IOException("Fetch failed", th);
                }
            }
        }
        int len = bb.remaining();
        int l = 0;
        Iterator<byte[]> it = processed.iterator();
        while (len > 0 && it.hasNext()) {
            byte[] buf = it.next();
            int sz = Math.min(len, buf.length - ppos);
            bb.put(buf, ppos, sz);
            len -= sz;
            ppos += sz;
            l += sz;
            if (buf.length == ppos) {
                it.remove();
                ppos = 0;
            }
            if (len == 0) {
                break;
            }
        }
        return l;
    }

}
