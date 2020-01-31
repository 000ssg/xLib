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
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author 000ssg
 */
public class Chars2Bytes implements BufferConverter<CharBuffer, ByteBuffer> {

    Charset encoding = Charset.forName("UTF-8");
    CharsetEncoder encoder;

    List<ByteBuffer> data = new ArrayList<>();
//    List<ByteBuffer> data = new ArrayList<ByteBuffer>() {
//        @Override
//        public boolean add(ByteBuffer e) {
//            System.out.println("data.add: " + BufferTools.dump(e).replace("\n", "\n  |  "));
//            return super.add(e);
//        }
//    };
    long lengthIn;
    long lengthOut;
    int size;
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    boolean eof = false;
    boolean flushOnDemand = true;

    public Chars2Bytes() {
        encoder = encoding.newEncoder();
    }

    public Chars2Bytes(String charset) {
        encoding = Charset.forName(charset);
        encoder = encoding.newEncoder();
    }

    public Chars2Bytes(Charset charset) {
        encoding = charset;
        encoder = encoding.newEncoder();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        Chars2Bytes copy = (Chars2Bytes) super.clone();
        copy.data = new ArrayList<>();
        for (ByteBuffer cb : data) {
            ByteBuffer cbi = ByteBuffer.allocate(cb.remaining());
            cbi.put(cb.duplicate());
            ((Buffer) cbi).flip();
            copy.data.add(cbi);
        }
        copy.buffer = ByteBuffer.allocate(buffer.capacity());
        copy.buffer.put((ByteBuffer) buffer.duplicate().flip());

        return copy;
    }

    public void reset() {
        data.clear();
        lengthIn = 0;
        lengthOut = 0;
        size = 0;
        ((Buffer) buffer).clear();
        eof = false;
        encoder.reset();
    }

    public List<ByteBuffer> remove() {
        List<ByteBuffer> r = new ArrayList<>();
        synchronized (data) {
            r.addAll(data);
            data.clear();
        }
        return r;
    }

    @Override
    public long getSourceLength() {
        return lengthIn;
    }

    @Override
    public long getTargetLength() {
        return lengthOut;
    }

    @Override
    public long getAvailable() {
        return size;
    }

    @Override
    public boolean isOpen() {
        return !eof;
    }

    @Override
    public void write(CharBuffer... bbs) throws IOException {
        if (bbs != null && !eof) {
            for (CharBuffer bb : bbs) {
                while (bb.hasRemaining()) {
                    lengthIn += bb.remaining();
                    CoderResult cr = encoder.encode(bb, buffer, eof);
                    lengthIn -= bb.remaining();
                    if (!buffer.hasRemaining() || cr.isOverflow() || eof) {
                        flush();
                    }
//                    if (bb.hasRemaining() && CoderResult.OVERFLOW == cr) {
//                        cr = encoder.encode(bb, buffer, eof);
//                        int a = 0;
//                    }
                }
            }
        }
    }

    @Override
    public void flush() {
        if (buffer.position() > 0) {
            ((Buffer) buffer).flip();
            lengthOut += buffer.remaining();
            size += buffer.remaining();
            if (buffer.remaining() > buffer.capacity() / 2 || eof) {
                data.add(buffer);
                if (!eof) {
                    buffer = ByteBuffer.allocate(buffer.capacity());
                } else {
                    buffer = null;
                }
            } else {
                ByteBuffer cb = ByteBuffer.allocate(buffer.remaining());
                cb.put(buffer);
                ((Buffer) cb).flip();
                buffer.compact();
                data.add(cb);
            }
        }
    }

    @Override
    public void close() throws IOException {
        eof = true;
        encoder.encode(CharBuffer.allocate(0), buffer, eof);
        flush();
    }

    @Override
    public int read(ByteBuffer bb) throws IOException {
        if (bb != null && size < bb.remaining() && flushOnDemand && buffer != null && buffer.position() > 0) {
            flush();
        }
        int c = 0;
        synchronized (data) {
            Iterator<ByteBuffer> it = data.iterator();
            if (bb != null) {
                while (it.hasNext() && bb.hasRemaining()) {
                    ByteBuffer cb = it.next();
                    while (cb.hasRemaining() && bb.hasRemaining()) {
                        bb.put(cb.get());
                        size--;
                        c++;
                    }
                    if (!cb.hasRemaining()) {
                        it.remove();
                    }
                }
            }
            if (eof && c == 0) {
                c = -1;
            }
            return c;
        }
    }

    @Override
    public ByteBuffer read(int expectedSize) throws IOException {
        if (size < expectedSize && flushOnDemand && buffer != null && buffer.position() > 0) {
            flush();
        }
        ByteBuffer r = ByteBuffer.allocate(Math.min(size, expectedSize));
        read(r);
        ((Buffer) r).flip();
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("\n  encoding=" + encoding);
        sb.append("\n  encoder=" + encoder);
        sb.append("\n  lengthIn=" + lengthIn);
        sb.append("\n  lengthOut=" + lengthOut);
        sb.append("\n  size=" + size);
        sb.append("\n  data=" + data);
        sb.append("\n  buffer=" + ((Buffer) buffer.duplicate()).flip());
        sb.append("\n  eof=" + eof);
        sb.append("\n}");
        return sb.toString();
    }
}
