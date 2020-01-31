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

/**
 *
 * @author sesidoro
 */
public class ByteBufferPipeUnchunk extends ByteBufferPipe {

    int chunk = -1;
    int skip = 0;
    byte last = -1;
    StringBuilder chSize = new StringBuilder();

    public ByteBufferPipeUnchunk() {
    }

    public ByteBufferPipeUnchunk(BufferPipe<ByteBuffer> nested) {
        super(nested);
    }

    @Override
    public void write(ByteBuffer... bbs) throws IOException {
        if (bbs == null || bbs.length == 0) {
            return;
        }
        boolean lastChunk = false;
        if (chunk == -1) {
            for (ByteBuffer bb : bbs) {
                if (bb == null || !bb.hasRemaining()) {
                    continue;
                }
                while (bb.hasRemaining()) {
                    byte b = bb.get();
                    if (skip > 0) {
                        skip--;
                        continue;
                    }
                    switch (b) {
                        case '\r':
                            break;
                        case '\n':
                            try {
                                if (last == '\r') {
                                    try {
                                        chunk = Integer.parseInt(chSize.toString(), 16);
                                        if (chunk == 0) {
                                            lastChunk = true;
                                            skip = 2;
                                        }
                                        last = -1;
                                        break;
                                    } catch (Throwable th) {
                                        if (th instanceof IOException) {
                                            throw (IOException) th;
                                        } else {
                                            throw new IOException("Unrecognized chunnk size: " + chSize, th);
                                        }
                                    }
                                } else {
                                    throw new IOException("Unrecognized chunk size: " + chSize);
                                }
                            } finally {
                                chSize.delete(0, chSize.length());
                            }
                        default:
                            chSize.append((char) (0xFF & b));
                    }
                    if (chunk != -1) {
                        break;
                    }
                    last = b;
                }
            }
        }
        {
            for (ByteBuffer bb : bbs) {
                if (bb != null && bb.hasRemaining()) {
                    if (skip > 0) {
                        while (skip > 0 && bb.hasRemaining()) {
                            bb.get();
                            skip--;
                        }
                    }
                    if (lastChunk) {
                        break;
                    }
                    int sz = Math.min(bb.remaining(), chunk);
                    if (bb.remaining() > sz) {
                        byte[] buf = new byte[sz];
                        bb.get(buf);
                        super.write(ByteBuffer.wrap(buf));
                    } else {
                        super.write(bb);
                    }
                    chunk -= sz;
                    if (chunk == 0) {
                        skip = 2;
                        chunk = -1;
                        write(bbs);
                    }
                }
            }
            if (lastChunk) {
                close();
            }
        }
    }

}
