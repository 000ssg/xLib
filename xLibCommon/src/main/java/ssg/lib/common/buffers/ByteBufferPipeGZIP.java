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
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author sesidoro
 */
public class ByteBufferPipeGZIP extends ByteBufferPipe {

    GZIPOutputStream os;

    public ByteBufferPipeGZIP() throws IOException {
    }

    public ByteBufferPipeGZIP(BufferPipe<ByteBuffer> nested) throws IOException {
        super(nested);
    }

    @Override
    public void write(ByteBuffer... bbs) throws IOException {
        if (os == null) {
            initOS();
        }
        if (bbs != null) {
            for (ByteBuffer bb : bbs) {
                if (bb != null && bb.hasRemaining()) {
                    byte[] buf = new byte[bb.remaining()];
                    bb.get(buf);
                    os.write(buf);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (os == null) {
                initOS();
            }
            os.flush();
            os.close();
        } catch (IOException ioex) {
        }
        super.close();
    }

    @Override
    public Object clone() {
        ByteBufferPipeGZIP copy = (ByteBufferPipeGZIP) super.clone();
        copy.initOS();
        return copy;
    }

    private void initOS() {
        try {
            os = new GZIPOutputStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    ByteBuffer bb = ByteBuffer.allocate(1);
                    bb.put((byte) b);
                    ((Buffer)bb).flip();
                    write0(bb);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    ByteBuffer bb = ByteBuffer.allocate(len);
                    bb.put(b, off, len);
                    ((Buffer)bb).flip();
                    write0(bb);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    ByteBuffer bb = ByteBuffer.allocate(b.length);
                    bb.put(b, 0, b.length);
                    ((Buffer)bb).flip();
                    write0(bb);
                }
            });
        } catch (IOException ioex) {
        }
    }

    private void write0(ByteBuffer... bbs) throws IOException {
        super.write(bbs);
    }

}
