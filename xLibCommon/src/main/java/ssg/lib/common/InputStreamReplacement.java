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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.buffers.ByteBufferPipeReplacement;

/**
 *
 * @author sesidoro
 */
public class InputStreamReplacement extends FilterInputStream {

    public boolean DEBUG = false;

    ByteBufferPipeReplacement pipe;

    public InputStreamReplacement(InputStream in, Replacement... replacements) {
        super(in);
        if (replacements != null) {
            pipe = new ByteBufferPipeReplacement(replacements);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int r = 0;
        if (pipe == null) {
            return super.read(b, off, len);
        } else {
            byte[] buf = new byte[1024];
            ByteBuffer bb = ByteBuffer.wrap(buf);

            while (len > 0 && !pipe.isClosed()) {
                // get into buf
                int c = super.read(buf, 0, buf.length);
                if (c == -1) {
                    pipe.close();
                }

                if (c > 0) {
                    bb.position(c);
                    bb.flip();
                    if (DEBUG) {
                        System.out.println("READ SRC[" + bb.remaining() + "]:\n   S|" + BufferTools.toText("ISO-8859-1", bb).replace("\n", "\n   S|"));
                    }
                    pipe.write(bb);
                    ((Buffer) bb).clear();
                }

                c = pipe.read(ByteBuffer.wrap(b, off, len));
                if (c > 0) {
                    off += c;
                    len -= c;
                    r += c;
                }
            }
        }
        return (r == 0 && pipe.isClosed()) ? -1 : r;
    }

    @Override
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int c = read();
        if (c < 0) {
            return c;
        } else {
            return 0xFF & buf[0];
        }
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = 0;
        byte[] skipBuf = new byte[1024];
        while (n > 0) {
            int l = (int) Math.min(n, skipBuf.length);
            int c = read(skipBuf, 0, l);
            if (c != -1) {
                n -= c;
                skipped += c;
            }
        }
        return skipped;
    }

}
