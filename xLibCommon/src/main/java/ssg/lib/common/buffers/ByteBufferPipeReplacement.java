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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import ssg.lib.common.Replacement;
import ssg.lib.common.Replacement.MATCH;
import ssg.lib.common.Replacement.Replacements;

/**
 *
 * @author 000ssg
 */
public class ByteBufferPipeReplacement extends ByteBufferPipe {

    long fetched = 0;
    /**
     * Replacement(s) definition
     */
    Replacement replace;
    /**
     *
     */
    byte[] fetch;
    int fMin;
    int fPos;
    int fLen;
    MATCH lastM = MATCH.none;
    int fPartial = 0;
    /**
     * accumulated current replaced data: filled by fetch, consumed by read
     */
    List<byte[]> processed = new ArrayList<byte[]>() {
//        @Override
//        public boolean add(byte[] e) {
//            if (e != null && e.length > 0) try {
//                System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].processed.add[" + e.length + ", at " + size() + ", mpllsl="+fMin+";"+fPos+";"+fLen+";"+lastM+";"+replace.getMatchState()+";"+replace.getSizes()[0]+"/"+replace.getSizes()[1]+"] " + new String(e, "ISO-8859-1").replace("\n", "\\n").replace("\r", "\\r"));
//            } catch (Throwable th) {
//            }
//            return super.add(e);
//        }
    };
    /**
     * Current position in top processed buffer - once consumed, buffer is
     * removed, ppos reset to 0.
     */
    int ppos = 0;

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
        if (replace != null && replace.length > 0) {
            if (replace.length == 1) {
                this.replace = replace[0];
            } else {
                this.replace = new Replacements(replace);
            }
        }
        int rsz = 0;
        for (Replacement r : replace) {
            r.setAutoReset(true);
            int[] ri = r.getSizes();
            if (ri != null) {
                rsz = Math.max(Math.max(ri[0], ri[1]), rsz);
            }
        }
        fetch = new byte[(Math.max(rsz + 1024, 1024 * 5))];
    }

    @Override
    public int read(ByteBuffer bb) throws IOException {
        // get next available portion of replaced data into processed
        int c = fetch();
        if (processed.isEmpty()) {
            if (c == 0) {
                return 0;
            } else if (c == -1) {
                return -1;
            }
        }

        int r = 0;
        synchronized (processed) {
            Iterator<byte[]> it = processed.iterator();
            int len = bb.remaining();
            while (len > 0 && it.hasNext()) {
                byte[] buf = it.next();
                int sz = Math.min(len, buf.length - ppos);
                bb.put(buf, ppos, sz);
                len -= sz;
                ppos += sz;
                r += sz;
                if (ppos == buf.length) {
                    it.remove();
                    ppos = 0;
                }
            }
        }

        return r;
    }

    synchronized int fetch() throws IOException {
        if (fLen == fPos) {
            // if fetch buffer was fully loaded -> reset, otherwise append
            if (fPos == fetch.length) {
                fPos = 0;
                fMin = 0;
            }
            int c = super.read(ByteBuffer.wrap(fetch, fPos, fetch.length - fPos));
            if (c == -1 || c == 0) {
                if (c == -1 && MATCH.partial == lastM && fMin < fPos) {
                    // flush remainder
                    synchronized (processed) {
                        processed.add(Arrays.copyOfRange(fetch, fMin, fPos));
                        c = fPos - fMin;
                        lastM = MATCH.none;
                    }
                }
                return c;
            }
            fetched += c;
            fLen = fPos + c;
        }
        if (fLen == fPos && isClosed()) {
            return -1;
        }

        int r = 0;
        while (fPos < fLen) {
            byte b = fetch[fPos++];
            r++;
            MATCH m = replace.next(b);
            switch (m) {
                case none:
                    if (lastM == MATCH.partial) {
                        replace.reset();
                    }
                    break;
                case partial:
                    if (lastM != MATCH.partial) {
                        fPartial = fPos - 1;
                    }
                    break;
                case exact:
                    int[] szs = replace.getSizes();
                    if (szs[0] == -1) {
                        int a = 0;
                        replace.getSizes();
                    }
                    synchronized (processed) {
                        // write pre-match bytes
                        if ((fPos - fMin) > szs[0]) {
                            processed.add(Arrays.copyOfRange(fetch, fMin, fPos - szs[0]));
                        }
                        // write replacement bytes
                        if (szs[1] > 0 && replace.getTrg() != null) {
                            processed.add(Arrays.copyOf(replace.getTrg(), szs[1]));
                        }
                    }
                    fMin = fPos;
                    fPartial = fMin;
                    replace.reset();
                    break;
            }
            if (fPos == fLen) {
                // flush unmatched bytes
                if (m == MATCH.partial) {
                    try {
                        synchronized (processed) { // save up to fPartial, then move fPartial to beginning...
                            processed.add(Arrays.copyOfRange(fetch, fMin, fPartial));
                            for (int i = 0; i < (fPos - fPartial); i++) {
                                fetch[i] = fetch[i + fPartial];
                            }
                            fLen = fPos - fPartial;
                            fPos = fLen;
                            fMin = 0;
                            fPartial = 0;
                        }
                    } catch (Throwable th) {
                        int a = 0;
                    }
                } else {
                    synchronized (processed) {
                        processed.add(Arrays.copyOfRange(fetch, fMin, fPos));
                        fLen = 0;
                        fPos = 0;
                        fMin = 0;
                    }
                }
            }
            lastM = m;
        }
        return r;
    }

    public long getFetchedSize() {
        return fetched;
    }

    public long getProcessedSize() {
        long l = 0;
        synchronized (processed) {
            for (byte[] bb : processed) {
                l += bb.length;
            }
        }
        return l;
    }
}
