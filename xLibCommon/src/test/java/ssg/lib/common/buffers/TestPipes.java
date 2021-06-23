/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.Arrays;
import ssg.lib.common.Replacement;
import ssg.lib.common.Replacement.Replacements;

/**
 *
 * @author sesidoro
 */
public class TestPipes {

    public static void main(String... args) throws Exception {
        //String source = "src/test/resources/test2.text";
        final String source = "src/test/resources/UI.html";
        final boolean traceStartStop = true;
        final boolean traceWrites = false;
        final int bufStep=16;
        try {

            Replacement r = new Replacements(
                    new Replacement("${app.name}", "A"),
                    new Replacement("${user.id}", "A.aaa")
            );

            int size = 31;
            final ByteBufferPipe[] pipes = new ByteBufferPipe[size];
            final long[][] counts = new long[size][2];
            final byte[][][] outputs = new byte[size][][];

            // 0-reader,1-writer
            final Thread[][] ths = new Thread[size][2];

            for (int i = 0; i < ths.length; i++) {
                final int idx = i;
                pipes[i] = new ByteBufferPipeReplacement(r.copy());

                ths[i][0] = new Thread() {
                    @Override
                    public void run() {
                        setName("reader-" + idx);
                        if (traceStartStop) {
                            System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].started");
                        }
                        try {
                            RandomAccessFile raf = new RandomAccessFile(source, "r");
                            ByteChannel ch = raf.getChannel();
                            int bufSize = (idx+1)*bufStep + 3;
                            ByteBuffer bb = ByteBuffer.allocate(bufSize);
                            int c = 0;
                            while ((c = ch.read(bb)) != -1) {
                                if (c > 0) {
                                    counts[idx][0] += c;
                                    bb.flip();
                                    if (traceWrites) {
                                        System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].write[" + bb.remaining() + "] " + BufferTools.toText("ISO-8859-1", bb).replace("\n", "\\n").replace("\r", "\\r"));
                                    }
                                    while (bb.hasRemaining()) {
                                        pipes[idx].write(bb);
                                    }
                                    bb = ByteBuffer.allocate(bufSize);
                                    Thread.sleep(Math.round(10 * Math.random()) + 5);
                                }
                            }
                        } catch (Throwable th) {
                            if (traceStartStop) {
                                System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].error: " + th);
                            }
                            th.printStackTrace();
                        } finally {
                            try {
                                pipes[idx].close();
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                            if (traceStartStop) {
                                System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].stopped");
                            }
                        }
                    }
                };
                ths[i][1] = new Thread() {
                    @Override
                    public void run() {
                        setName("writer-" + idx);
                        if (traceStartStop) {
                            System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].started");
                        }
                        try {
                            byte[] buf = new byte[80];
                            ByteBuffer bb = ByteBuffer.wrap(buf);
                            int c = 0;
                            while ((c = pipes[idx].read(bb)) != -1) {
                                if (c > 0) {
                                    counts[idx][1] += c;
                                    //synchronized (outputs[idx]) {
                                    if (traceWrites) {
                                        System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].write[" + c + "] " + new String(buf, 0, c, "ISO-8859-1").replace("\n", "\\n").replace("\r", "\\r"));
                                    }
                                    byte[] bs = Arrays.copyOf(buf, c);
                                    if (outputs[idx] == null) {
                                        outputs[idx] = new byte[][]{bs};
                                    } else {
                                        outputs[idx] = Arrays.copyOf(outputs[idx], outputs[idx].length + 1);
                                        outputs[idx][outputs[idx].length - 1] = bs;
                                    }
                                    //}
                                    bb.clear();
                                }
                            }
                        } catch (Throwable th) {
                            if (traceStartStop) {
                                System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].error: " + th);
                                th.printStackTrace();
                            }
                        } finally {
                            if (traceStartStop) {
                                System.out.println("[" + System.currentTimeMillis() + "][" + getName() + "].stopped");
                            }
                        }
                    }
                };
                ths[i][0].setDaemon(true);
                ths[i][1].setDaemon(true);
                ths[i][0].start();
                ths[i][1].start();
            }

            int count = 1;
            while (count > 0) {
                count = 0;
                for (Thread[] ts : ths) {
                    if (ts[0] != null) {
                        if (ts[0].isAlive()) {
                            count++;
                        }
                        if (ts[1].isAlive()) {
                            count++;
                        }
                    }
                }
            }

            System.out.println("Totals running[" + size + "]:");
            String refOutput = toText(outputs[0]);
            for (int i = 0; i < size; i++) {
                System.out.println("  " + counts[i][0] + "\t\t" + counts[i][1]+"\t"+(toText(outputs[0]).equals(refOutput)));
            }

            for (int i = 0; i < size; i++) {
                System.out.println("[" + i + "][" + toBytes(outputs[i]).length + "]\t" + toText(outputs[i]).replace("\n", "\\n").replace("\r", "\\r"));
                String s = toText(outputs[i]);
                int a = 0;
            }

            int a = 0;
        } finally {
            //
        }
    }

    public static byte[] toBytes(byte[][] bbs) {
        if (bbs == null) {
            return null;
        }
        int sz = 0;
        for (byte[] bs : bbs) {
            if (bs != null) {
                sz += bs.length;
            }
        }
        byte[] buf = new byte[sz];
        sz = 0;
        for (byte[] bs : bbs) {
            if (bs != null && bs.length > 0) {
                System.arraycopy(bs, 0, buf, sz, bs.length);
                sz += bs.length;
            }
        }
        return buf;
    }

    public static String toText(byte[][] bbs) {
        byte[] bs = toBytes(bbs);
        try {
            return bs != null ? new String(bs, "ISO-8859-1") : null;
        } catch (Throwable th) {
            return null;
        }
    }
}
