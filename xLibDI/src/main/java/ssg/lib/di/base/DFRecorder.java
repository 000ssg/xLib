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
package ssg.lib.di.base;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DM;

/**
 * Traffic recorder. Uses ByteBuffer for logging, accepts ByteBuffer or
 * CharBuffer as tracked data.
 *
 * @author 000ssg
 */
public class DFRecorder<T extends Buffer, P> extends BaseDF<T, P> {

    public static enum MODE {
        standard, // separate files for read and write operations per channel
        joined, // separate file per channel (both read/write)
        full // all channels read/write into same file
    }

    MODE mode = MODE.standard;
    File folder;
    Map<P, ByteChannel[]> recorders = new HashMap<>();
    ProviderToFileName p2fn;
    ByteChannel full;

    public DFRecorder() {
    }

    public DFRecorder(File folder) {
        this.folder = folder;
    }

    public <Z extends DFRecorder> Z providerToFileName(ProviderToFileName p2fn) {
        this.p2fn = p2fn;
        return (Z) this;
    }

    public <Z extends DFRecorder> Z joined() {
        mode = MODE.joined;
        return (Z) this;
    }

    public <Z extends DFRecorder> Z fullRecorder(File path) throws IOException {
        if (path != null) {
            full = new RandomAccessFile(path, "rw").getChannel();
            mode = MODE.full;
        }
        return (Z) this;
    }

    public String getProviderNameForFile(P provider) {
        String fn = (p2fn != null) ? p2fn.getProviderNameForFile(provider) : "" + provider;
        for (char ch : "!\"#¤%&/()=?`@£${[]}\\´¨^'*<>|~,.;:§½".toCharArray()) {
            fn = fn.replace(ch, '_');
        }
        return fn;
    }

    public void doRecord(P provider, ByteChannel os, boolean input, Collection<T>... data) throws IOException {
        if (os == null || data == null || data.length == 0) {
            return;
        }
        Collection<ByteBuffer> buf = new ArrayList<ByteBuffer>();
        for (Collection<T> d : data) {
            if (d == null || d.isEmpty()) {
                continue;
            }
            for (T t : d) {
                if (!t.hasRemaining()) {
                    continue;
                }
                if (t instanceof ByteBuffer) {
                    buf.add(((ByteBuffer) t).duplicate());
                } else if (t instanceof CharBuffer) {
                    CharBuffer cb = ((CharBuffer) t).duplicate();
                    buf.add(ByteBuffer.wrap(cb.toString().getBytes("UTF-8")));
                } else {
                    //
                }
            }
        }
        if (!buf.isEmpty()) {
            writeRecord(provider, os, input, buf);
        }
    }

    public synchronized void writeRecord(P provider, ByteChannel os, boolean input, Collection<ByteBuffer> data) throws IOException {
        long c = BufferTools.getRemaining(data);
        if (data != null) {
            for (ByteBuffer buf : data) {
                if (buf == null || !buf.hasRemaining()) {
                    continue;
                }
                flushRecord(os, buf);
            }
        }
    }

    public void flushRecord(ByteChannel os, ByteBuffer buf) throws IOException {
        int c1 = buf.remaining();
        int c2 = 0;
        while (c2 < c1) {
            int c3 = os.write(buf);
            if (c3 > 0) {
                c2 += c3;
            }
        }
    }

    public File getReadRecorderFile(P provider) throws IOException {
        String fn = getProviderNameForFile(provider) + "_read";
        if (folder != null) {
            return new File(folder, fn);
        } else {
            return File.createTempFile("" + getClass().getSimpleName() + "_", "_" + fn);
        }
    }

    public File getWriteRecorderFile(P provider) throws IOException {
        String fn = getProviderNameForFile(provider) + "_write";
        if (folder != null) {
            return new File(folder, fn);
        } else {
            return File.createTempFile("" + getClass().getSimpleName() + "_", "_" + fn);
        }
    }

    public synchronized ByteChannel[] createRecorders(P provider) throws IOException {
        ByteChannel[] r = new ByteChannel[2];
        if (mode == MODE.full) {
            r[0] = full;
            r[1] = full;
        } else {
            File rr = this.getReadRecorderFile(provider);
            File rw = this.getWriteRecorderFile(provider);
            r[0] = (rr != null) ? new RandomAccessFile(rr, "rw").getChannel() : null;
            r[1] = (mode == MODE.joined) ? r[0] : (rw != null) ? new RandomAccessFile(rw, "rw").getChannel() : null;
        }
        recorders.put(provider, r);
        return r;
    }

    public ByteChannel[] getRecorders(P provider) throws IOException {
        ByteChannel[] r = recorders.get(provider);
        if (r == null) {
            r = createRecorders(provider);
        }
        return r;
    }

    @Override
    public void delete(P provider) throws IOException {
        ByteChannel[] rs = recorders.remove(provider);
        if (rs != null) {
            if (mode == MODE.full) {

            } else {
                if (rs[0] != null) {
                    try {
                        rs[0].close();
                    } catch (Throwable th) {
                    }
                }
                if (rs[1] != null) {
                    try {
                        if (rs[0] != rs[1]) {
                            rs[1].close();
                        }
                    } catch (Throwable th) {
                    }
                }
            }
        }
        super.delete(provider);
    }

    @Override
    public List<T> writeFilter(DM<P> owner, P provider, Collection<T>... data) throws IOException {
        List<T> r = null;
        if (filter() != null) {
            r = filter.onWrite(owner, provider, data);
        } else if (data != null && data.length > 0) {
            r = new ArrayList<>();
            for (Collection<T> d : data) {
                if (d != null) {
                    r.addAll(d);
                }
            }
        }
        if (r != null && !r.isEmpty()) {
            ByteChannel[] rs = getRecorders(provider);
            doRecord(provider, rs[1], false, r);
        }
        return r;
    }

    @Override
    public List<T> readFilter(DM<P> owner, P provider, Collection<T>... data) throws IOException {
        if (data != null && data.length > 0) {
            ByteChannel[] rs = getRecorders(provider);
            doRecord(provider, rs[0], true, data);
        }
        List<T> r = null;
        if (filter() != null) {
            r = filter.onRead(owner, provider, data);
        } else if (data != null && data.length > 0) {
            r = new ArrayList<>();
            for (Collection<T> d : data) {
                if (d != null) {
                    r.addAll(d);
                }
            }
        }
        return r;
    }

    public static interface ProviderToFileName<P> {

        String getProviderNameForFile(P provider);
    }
}
