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

/**
 *
 * @author 000ssg
 */
public class ByteBufferPipeChunk extends ByteBufferPipe {

    int chunkSize = 4096;
    boolean chunkOK = false;
    boolean chunkEOF = false;
    ByteBuffer chunkPrefix = ByteBuffer.allocate(10 + 4);
    ByteBuffer chunk;
    ByteBuffer chunkSuffix = ByteBuffer.allocate(2);

    public ByteBufferPipeChunk() {
        chunk = ByteBuffer.allocate(chunkSize);
    }

    public ByteBufferPipeChunk(BufferPipe nested) {
        super(nested);
        chunk = ByteBuffer.allocate(chunkSize);
    }

    public ByteBufferPipeChunk(int chunkSize) {
        if (chunkSize > 0) {
            this.chunkSize = chunkSize;
        }
        chunk = ByteBuffer.allocate(chunkSize);
    }

    @Override
    public int read(ByteBuffer bb) throws IOException {
        if (chunkPrefix == null && chunkEOF) {
            int cc = 0;
            if (chunkSuffix != null && chunkSuffix.hasRemaining()) {
                // has unsent suffix, but no place for it -> return 0 (next non-empty buffer should help...
                if (!bb.hasRemaining()) {
                    return 0;
                }
                while (bb.hasRemaining() && chunkSuffix.hasRemaining()) {
                    bb.put(chunkSuffix.get());
                    cc++;
                }
                if (!chunkSuffix.hasRemaining()) {
                    chunkOK = false;
                }
            }
            // if had unfinished suffix ->  return suffix bytes, otherwise EOF...
            return (cc > 0) ? cc : -1;
        }
        int r = bb.remaining();
        if (chunkOK && bb.hasRemaining() && chunkPrefix.hasRemaining()) {
            while (bb.hasRemaining() && chunkPrefix.hasRemaining()) {
                bb.put(chunkPrefix.get());
            }
        }
        if (chunkOK && bb.hasRemaining() && chunk.hasRemaining()) {
            while (bb.hasRemaining() && chunk.hasRemaining()) {
                bb.put(chunk.get());
            }
        }
        if (chunkOK && bb.hasRemaining() && chunkSuffix.hasRemaining()) {
            while (bb.hasRemaining() && chunkSuffix.hasRemaining()) {
                bb.put(chunkSuffix.get());
            }
            if (!chunkSuffix.hasRemaining()) {
                chunkOK = false;
            }
        }
        if (!chunkOK || chunkOK && !chunkSuffix.hasRemaining()) {
            if (size == 0 && closed) {
                if (chunkPrefix == null) {
                    return -1;
                }
            }
            //
            if (chunkEOF) {
                if (r == 0) {
                    return -1;
                }
            } else {
                ((Buffer) chunkPrefix).clear();
                ((Buffer) chunk).clear();
                ((Buffer) chunkSuffix).clear();
                int sz = (int) Math.min(chunkSize, size);
                if (sz < chunkSize && !closed) {
                    chunkOK = false;
                } else {
                    if (chunkEOF) {
                        return -1;
                    }
                    chunkOK = true;
                    int cc = super.read(chunk);
                    if (cc == 0) {
                        return 0;
                    } else if (cc == -1) {
                        chunkEOF = true;
                    }
                    ((Buffer) chunk).flip();
                    chunkPrefix.put((Integer.toHexString((cc > 0) ? cc : 0) + "\r\n").getBytes());
                    ((Buffer) chunkPrefix).flip();
                    chunkSuffix.put("\r\n".getBytes());
                    ((Buffer) chunkSuffix).flip();
                    if (bb.hasRemaining()) {
                        read(bb);
                    }
                    if (cc == -1) {
                        chunkPrefix = null;
                    }
                }
            }
        }
        return r - bb.remaining();
    }

}
