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
package ssg.lib.net.t1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import ssg.lib.common.CommonTools;
import ssg.lib.common.Config;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.net.MCS;
import ssg.lib.net.MCS.MCSConfig;
import ssg.lib.net.MCS.RunnerIO;
import ssg.lib.net.stat.MCSStatistics;
import ssg.lib.net.stat.RunnerStatisticsImpl;

/**
 *
 * @author 000ssg
 */
public class Test_MCS {

    public static void main(String... args) throws Exception {
        System.out.println("MCS setup");
        MCS mcs = new MCS(2, 2) {
            @Override
            public RunnerIO onConnected(MCS.Runner runner, SocketChannel ch, RunnerIO attachment) {
                return new MCSMonitor(ch, runner, super.onConnected(runner, ch, attachment));
            }

            @Override
            public RunnerIO onAccepted(MCS.Runner runner, SocketChannel ch, RunnerIO attachment) {
                return new MCSMonitor(ch, runner, super.onConnected(runner, ch, attachment));
            }
        }
                .configureName("MCS-Test")
                .configureStatistics(new MCSStatistics("MCS-root", new RunnerStatisticsImpl()));
        System.out.println("... start");
        mcs.start();

        int port = 10000;
        mcs.listen(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port), (sc, client) -> {
            return new RunnerIO() {
                boolean active = true;
                List<ByteBuffer> out = new ArrayList<>();

                @Override
                public boolean isActive() {
                    return active;
                }

                @Override
                public long onRead(ByteBuffer bb) {
                    if (bb == null) {
                        active = false;
                        return -1;
                    } else {
                        byte[] buf = new byte[bb.remaining()];
                        int off = 0;
                        while (bb.hasRemaining()) {
                            buf[off++] = bb.get();
                        }
                        String s = new String(buf);
                        //System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "]" + s);
                        if (s.endsWith("Bye.\n")) {
                            active = false;
                        } else {
                            synchronized (out) {
                                out.add(ByteBuffer.wrap(("RPL:" + s).getBytes()));
                            }
                        }
                        return buf.length;
                    }
                }

                @Override
                public ByteBuffer onWrite(SocketChannel sc) {
                    synchronized (out) {
                        return out.isEmpty() ? null : out.remove(0);
                    }
                }
            };
        });

        for (int i = 0; i < 21; i++) {
            mcs.connect(new URI("aaa://localhost:" + port), (sc, client) -> {
                return new RunnerIO() {
                    List<String> messages = new ArrayList<String>() {
                        {
                            add("AAA1");
                            add("AAA2");
                            add("AAA3");
                            add("AAA4");
                            add("AAA5");
                            add("AAA6");
                            add("Bye.");
                        }
                    };
                    boolean waitResponse = false;

                    @Override
                    public boolean isActive() {
                        return !messages.isEmpty() || waitResponse;
                    }

                    @Override
                    public long onRead(ByteBuffer bb) {
                        if (bb != null && bb.hasRemaining() && waitResponse) {
                            byte[] buf = new byte[bb.remaining()];
                            int off = 0;
                            while (bb.hasRemaining()) {
                                buf[off++] = bb.get();
                            }
                            String s = new String(buf);
                            if (bb != null && bb.hasRemaining()) {
                                //System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "]" + s);
                            }
                            waitResponse = false;
                            return buf.length;
                        } else {
                            synchronized (messages) {
                                return messages.isEmpty() && !waitResponse ? -1 : 0;
                            }
                        }
                    }

                    @Override
                    public ByteBuffer onWrite(SocketChannel sc
                    ) {
                        if (!waitResponse) {
                            synchronized (messages) {
                                if (messages.isEmpty()) {
                                    return null;
                                }
                                waitResponse = true;
                                String s = messages.remove(0);
                                return ByteBuffer.wrap((s + "\n").getBytes());
                            }
                        } else {
                            return null;
                        }
                    }
                };
            });
        }

        System.out.println(mcs);
        System.out.println("... sleep");
        NetTools.delay(1000);// * 60);// * 5);
        System.out.println("... stop");
        System.out.println(mcs);
        mcs.stop();
        System.out.println("... stopped");
        System.out.println(mcs);

        System.out.println("... sleep (while all mcs stuff is completed)");
        NetTools.delay(1000);

        Throwable th;
        System.out.println("\nTest MCSConfigs: a and b");
        String base = Test_MCS.class.getCanonicalName();
        for (Object[] oo : new Object[][]{
            {Test_MCS.class.getClassLoader().getResource("mcs.a")},
            {Test_MCS.class.getClassLoader().getResource("mcs.b"), "net.mcs"}
        }) {
            URL cfg = (URL) oo[0];
            String baseCfg = oo.length > 1 ? (String) oo[1] : null;
            MCS mcst = new MCS().configuration(Config.load(baseCfg != null ? new MCSConfig(baseCfg) : new MCSConfig(), "configURL=" + cfg));
            System.out.println(""
                    + "\n\n--------------------------------------\n-- Config: base=" + (baseCfg != null ? baseCfg : "<default>") + ", resource=" + cfg
                    + "\n------- CFG\n-- " + (cfg != null ? new String(CommonTools.loadInputStream(cfg.openStream()), "ISO-8859-1").replace("\n", "\n-- ") : "")
                    + "\n------- MCS\n-- " + mcst.toString().replace("\n", "\n-- ")
            );
        }

    }

    public static class MCSMonitor extends MCS.RunnerIOWrapper {

        SocketChannel ch;
        MCS.Runner runner;
        boolean binary = false;

        public MCSMonitor(SocketChannel ch, MCS.Runner runner, RunnerIO base) {
            super(base);
            this.ch = ch;
            this.runner = runner;
        }

        public MCSMonitor configureBin(boolean binary) {
            this.binary = binary;
            return this;
        }

        public void info(String method, byte[] text) {
            try {
                System.out.println(""
                        + "[" + System.currentTimeMillis() + "]"
                        + "[" + Thread.currentThread().getName() + "]"
                        + "[" + runner.name() + "]"
                        + "[" + System.identityHashCode(ch) + "::" + ch + "]." + method + ": "
                        + (text != null ? binary ? "\n  " + BufferTools.dump(text).replace("\n", "\n  ") : new String(text, "ISO-8859-1").replace("\n", "\\n") : ""));
            } catch (Throwable th) {
            }
        }

        @Override
        public ByteBuffer onWrite(SocketChannel sc) throws IOException {
            byte[] text = null;
            try {
                ByteBuffer r = super.onWrite(sc);
                if (r != null && r.hasRemaining()) {
                    text = new byte[r.remaining()];
                    r.mark();
                    int off = 0;
                    while (r.hasRemaining()) {
                        text[off++] = r.get();
                    }
                    r.reset();
                }
                return r;
            } finally {
                if (text != null) {
                    info("onWrite", text);
                }
            }
        }

        @Override
        public long onRead(ByteBuffer buf) throws IOException {
            byte[] text = null;
            try {
                if (buf != null && buf.hasRemaining()) {
                    text = new byte[buf.remaining()];
                    buf.mark();
                    int off = 0;
                    while (buf.hasRemaining()) {
                        text[off++] = buf.get();
                    }
                    buf.reset();
                }
                long c = super.onRead(buf);
                return c;
            } finally {
                info("onRead ", text);
            }
        }

        @Override
        public void onClose(SocketChannel sc, Throwable th) throws IOException {
            try {
                super.onClose(sc, th);
            } finally {
                info("onClose", null);
            }
        }

        @Override
        public void onOpen(SocketChannel sc) throws IOException {
            try {
                super.onOpen(sc);
            } finally {
                info("onOpen", null);
            }
        }
    }

}
