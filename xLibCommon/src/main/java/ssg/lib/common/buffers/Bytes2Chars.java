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
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author sesidoro
 */
public class Bytes2Chars implements BufferConverter<ByteBuffer, CharBuffer> {

    Charset encoding = Charset.forName("UTF-8");
    CharsetDecoder decoder;

    List<CharBuffer> data = new ArrayList<>();
    long lengthIn;
    long lengthOut;
    int size;
    ByteBuffer reminder;
    CharBuffer buffer = CharBuffer.allocate(1024);
    boolean eof = false;
    boolean flushOnDemand = true;

    public Bytes2Chars() {
        decoder = encoding.newDecoder();
    }

    public Bytes2Chars(String charset) {
        encoding = Charset.forName(charset);
        decoder = encoding.newDecoder();
    }

    public Bytes2Chars(Charset charset) {
        encoding = charset;
        decoder = encoding.newDecoder();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        Bytes2Chars copy = (Bytes2Chars) super.clone();
        copy.data = new ArrayList<>();
        for (CharBuffer cb : data) {
            CharBuffer cbi = CharBuffer.allocate(cb.remaining());
            cbi.put(cb.duplicate());
            ((Buffer) cbi).flip();
            copy.data.add(cbi);
        }
        copy.buffer = CharBuffer.allocate(buffer.duplicate().flip().capacity());
        copy.buffer.put((CharBuffer) buffer.duplicate().flip());

        if (reminder != null) {
            copy.reminder = ByteBuffer.allocate(reminder.capacity());
            copy.reminder.put((ByteBuffer) reminder.duplicate().flip());
        }

        return copy;
    }

    public void reset() {
        data.clear();
        lengthIn = 0;
        lengthOut = 0;
        size = 0;
        reminder = null;
        ((Buffer) buffer).clear();
        eof = false;
        decoder.reset();
    }

    public List<CharBuffer> remove() {
        List<CharBuffer> r = new ArrayList<>();
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
    public void write(ByteBuffer... bbs) throws IOException {
        if (bbs != null) {
            for (ByteBuffer bb : bbs) {
                if (bb == null || !bb.hasRemaining()) {
                    continue;
                }
                if (reminder != null) {
                    ByteBuffer tmp = ByteBuffer.allocate(reminder.remaining() + bb.remaining());
                    tmp.put(reminder).put(bb).flip();
                    bb = tmp;
                    reminder = null;
                }
                lengthIn += bb.remaining();
                CoderResult cr = decoder.decode(bb, buffer, eof);
                lengthIn -= bb.remaining();
                if (bb.hasRemaining() && cr.isUnderflow()) {
                    reminder = ByteBuffer.allocate(bb.remaining());
                    reminder.put(bb);
                    ((Buffer) reminder).flip();
                }
                if (!buffer.hasRemaining() || eof) {
                    flush();
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
                    buffer = CharBuffer.allocate(buffer.capacity());
                } else {
                    buffer = null;
                }
            } else {
                CharBuffer cb = CharBuffer.allocate(buffer.remaining());
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
        flush();
    }

    @Override
    public int read(CharBuffer bb) throws IOException {
        synchronized (data) {
            if (bb == null) {
                return 0;
            }
            if (bb != null && flushOnDemand && bb.remaining() > size && buffer != null && buffer.position() > 0) {
                flush();
            }
            Iterator<CharBuffer> it = data.iterator();
            int c = 0;
            while (it.hasNext() && bb.hasRemaining()) {
                CharBuffer cb = it.next();
                while (cb.hasRemaining() && bb.hasRemaining()) {
                    bb.put(cb.get());
                    size--;
                    c++;
                }
                if (!cb.hasRemaining()) {
                    it.remove();
                }
            }
            return c;
        }
    }

    @Override
    public CharBuffer read(int expectedSize) throws IOException {
        if (size < expectedSize && flushOnDemand && buffer != null && buffer.position() > 0) {
            flush();
        }
        CharBuffer r = CharBuffer.allocate(Math.min(size, expectedSize));
        read(r);
        ((Buffer) r).flip();
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("\n  encoding=" + encoding);
        sb.append("\n  decoder=" + decoder);
        sb.append("\n  lengthIn=" + lengthIn);
        sb.append("\n  lengthOut=" + lengthOut);
        sb.append("\n  size=" + size);
        sb.append("\n  data=" + data);
        sb.append("\n  reminder=" + reminder);
        sb.append("\n  buffer=" + ((buffer != null) ? ((Buffer) buffer.duplicate()).flip() : "null"));
        sb.append("\n  eof=" + eof);
        sb.append("\n}");
        return sb.toString();
    }
}
