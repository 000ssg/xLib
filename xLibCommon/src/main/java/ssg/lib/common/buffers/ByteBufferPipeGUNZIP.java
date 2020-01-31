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
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author sesidoro
 */
public class ByteBufferPipeGUNZIP extends ByteBufferPipe {

    GZIPInputStream is;
    ByteBufferPipe isBuf = new ByteBufferPipe();

    public ByteBufferPipeGUNZIP() throws IOException {
    }

    public ByteBufferPipeGUNZIP(BufferPipe<ByteBuffer> nested) throws IOException {
        super(nested);
    }

    @Override
    public void write(ByteBuffer... bbs) throws IOException {
        if (closed) {
            throw new IOException("Write after EOF");
        }
        isBuf.write(bbs);
        if (is == null && isBuf.size < 10) {
            // ignore GUNZIP until got initial 10 bytes...
            return;
        }
        byte[] buf = new byte[1024];
        int c = 0;
        if (is == null) {
            is = new GZIPInputStream(new InputStream() {
                ByteBuffer b1 = ByteBuffer.allocate(1);

                @Override
                public int read() throws IOException {
                    if (closed) {
                        return -1;
                    }
                    ((Buffer) b1).clear();
                    int c = isBuf.read(b1);
                    ((Buffer) b1).flip();
                    return (c == -1) ? -1 : (0xFF & b1.get());
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (closed) {
                        return -1;
                    }
                    ByteBuffer bb = ByteBuffer.wrap(b, off, len);
                    int c = isBuf.read(bb);
                    // prevent blocking...
                    return (c == 0) ? -1 : c;
                }
            });
        }
        try {
            while (is.available() > 0 && (c = is.read(buf)) > 0) {
                ByteBuffer bb = ByteBuffer.allocate(c);
                bb.put(buf, 0, c);
                ((Buffer) bb).flip();
                super.write(bb);
            }
        } catch (BufferUnderflowException buex) {
        } catch (IOException ioex) {
            if (isBuf.isClosed()) {
                throw ioex;
            }
            //buex.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException ioex) {
        }
        super.close();
    }

    @Override
    public Object clone() {
        ByteBufferPipeGUNZIP copy = (ByteBufferPipeGUNZIP) super.clone();
        copy.is = null;
        copy.isBuf = new ByteBufferPipe();
        return copy;
    }

}
