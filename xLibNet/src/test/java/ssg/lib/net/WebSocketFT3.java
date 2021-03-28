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
package ssg.lib.net;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.common.CommonTools;
import ssg.lib.common.JSON;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.di.base.DFRecorder;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocket.WebSocketAddons;
import ssg.lib.websocket.WebSocketExtension;
import ssg.lib.websocket.WebSocketFrame;
import ssg.lib.websocket.WebSocketProcessor.BufferingMessageListener;
import ssg.lib.websocket.extensions.WebSocketExtensionGZip;
import ssg.lib.websocket.extensions.WebSocketExtensionTimestamp;
import ssg.lib.websocket.impl.DI_WS;
import ssg.lib.websocket.impl.WebSocketChannel;

/**
 *
 * @author 000ssg
 */
public class WebSocketFT3 {

    static synchronized void flush(ByteChannel ch, Collection<ByteBuffer> data) {
        ByteBuffer[] bufs = null;
        synchronized (data) {
            bufs = data.toArray(new ByteBuffer[data.size()]);
            data.clear();
        }

        try {
            for (ByteBuffer buf : bufs) {
                if (buf == null || !buf.hasRemaining()) {
                    continue;
                }
                int c = buf.remaining();
                int c1 = 0;
                while (c1 < c) {
                    int c2 = ch.write(buf);
                    if (c2 == -1) {
                        break;
                    }
                    c1 += c2;
                }
            }
        } catch (IOException ioex) {
        }
    }

    public static void main(String... args) throws Exception {
        //WebSocket.allExtensions.clear();

        final AtomicInteger callCount = new AtomicInteger();
        final AtomicInteger srvCount = new AtomicInteger();
        final AtomicInteger respCount = new AtomicInteger();

        File folder = new File("./target/dfr");
        FileChannel fmChannel = null;
        List<ByteBuffer> fmCache = Collections.synchronizedList(new ArrayList<>());

        CS cs = new CS();
        cs.start();
        try {

            folder.mkdirs();
            DFRecorder<ByteBuffer, SocketChannel> drS = new DFRecorder<ByteBuffer, SocketChannel>(folder) {
                @Override
                public void writeRecord(SocketChannel provider, ByteChannel os, boolean input, Collection<ByteBuffer> data) throws IOException {
                    List<ByteBuffer> bufs = new ArrayList<>();
                    bufs.add(ByteBuffer.wrap(("\n"
                            + "[" + System.currentTimeMillis()
                            + "][" + (input ? "R" : "W") + "][" + Thread.currentThread().getName()
                            + "][" + provider.getRemoteAddress() + "]").getBytes("UTF-8")));
                    bufs.add(ByteBuffer.wrap(("  " + BufferTools.dump(data).replace("\n", "\n  ")).getBytes("UTF-8")));
                    super.writeRecord(provider, os, input, bufs);
                }
            }.fullRecorder(new File(folder, "s.rec"));
            DFRecorder<ByteBuffer, SocketChannel> drC = new DFRecorder<ByteBuffer, SocketChannel>(folder) {
                @Override
                public void writeRecord(SocketChannel provider, ByteChannel os, boolean input, Collection<ByteBuffer> data) throws IOException {
                    List<ByteBuffer> bufs = new ArrayList<>();
                    bufs.add(ByteBuffer.wrap(("\n"
                            + "[" + System.currentTimeMillis()
                            + "][" + (input ? "R" : "W") + "][" + Thread.currentThread().getName()
                            + "][" + provider.getLocalAddress() + "]").getBytes("UTF-8")));
                    bufs.add(ByteBuffer.wrap(("  " + BufferTools.dump(data).replace("\n", "\n  ")).getBytes("UTF-8")));
                    super.writeRecord(provider, os, input, bufs);
                }
            }.fullRecorder(new File(folder, "c.rec"));

            fmChannel = new RandomAccessFile(new File(folder, "fm.rec"), "rw").getChannel();
            final FileChannel fmCh = fmChannel;
            final WebSocket.FrameMonitor fm = new WebSocket.FrameMonitor() {
                boolean compact = false;

                @Override
                public boolean check(long options, WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy) {
                    return (options & WebSocket.FrameMonitor.IF_UNMASKED) != 0;
                }

                @Override
                public void onCompletedFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload) {
                    if (1 == 1) {
                        //return;
                    }
                    String x = "";
                    if (frame != null && frame.getMask() != null) {
                        x += " m[";
                        for (byte b : frame.getMask()) {
                            x += ' ' + Integer.toHexString(0xFF & b);
                        }
                        x += "]";
                    }

                    String s = (" RECV[" + ((ws != null) ? ws.id + "/" + (ws.isClient() ? "C" : "S") : processedBy) + ", op=" + frame.getOpCode() + ";fin=" + frame.isFinalFragment() + x + "]" + ((compact) ? " size=" + BufferTools.getRemaining(payload) : "\n  " + BufferTools.dump(payload).replace("\n", "\n  ")));

                    try {
                        List<ByteBuffer> bufs = new ArrayList<>();
                        try {
                            bufs.add(ByteBuffer.wrap(("\n"
                                    + "[" + System.currentTimeMillis()
                                    + "][R][" + Thread.currentThread().getName()
                                    + "][" + ((WebSocketChannel) ws).channelInfo() + "]").getBytes("UTF-8")));
                        } catch (Throwable th) {
                        }
                        bufs.add(ByteBuffer.wrap((s.replace("\n", "\n  ")).getBytes("UTF-8")));

                        fmCache.addAll(bufs);
                        if (fmCache.size() > 10) {
                            flush(fmCh, fmCache);
                        }
                    } catch (Throwable th) {
                    }
                }

                @Override
                public void onOutgoingFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload, Integer off) {
                    if (1 == 1) {
                        //return;
                    }
                    String x = "";
                    if (frame != null && frame.getMask() != null) {
                        x += " m[";
                        for (byte b : frame.getMask()) {
                            x += ' ' + Integer.toHexString(0xFF & b);
                        }
                        x += "]";
                    }
                    String s = (" SEND[" + ((ws != null) ? ws.id + "/" + (ws.isClient() ? "C" : "S") : processedBy) + "," + off + ", op=" + frame.getOpCode() + ";fin=" + frame.isFinalFragment() + x + "]" + ((compact) ? " size=" + BufferTools.getRemaining(payload) : "\n  " + BufferTools.dump(payload).replace("\n", "\n  ")));

                    try {
                        List<ByteBuffer> bufs = new ArrayList<>();
                        try {
                            bufs.add(ByteBuffer.wrap(("\n"
                                    + "[" + System.currentTimeMillis()
                                    + "][W][" + Thread.currentThread().getName()
                                    + "][" + ((WebSocketChannel) ws).channelInfo() + "]").getBytes("UTF-8")));
                        } catch (Throwable th) {
                        }
                        bufs.add(ByteBuffer.wrap((s.replace("\n", "\n  ")).getBytes("UTF-8")));
                        fmCache.addAll(bufs);
                        if (fmCache.size() > 10) {
                            flush(fmCh, fmCache);
                        }
                    } catch (Throwable th) {
                    }
                }
            };

            DI_WS ws = new DI_WS() {
                JSON.Encoder encode = new JSON.Encoder();
                JSON.Decoder decode = new JSON.Decoder();

                @Override
                public WebSocket createWebSocket(Channel provider, boolean client, WebSocketAddons addOns) {
                    WebSocket r = super.createWebSocket(provider, client, addOns);
                    //r.setFrameMonitor(fm);
                    if (r instanceof WebSocketChannel) {
                        ((WebSocketChannel) r).DUMP = true;
                    }
                    return r;
                }

                @Override
                public boolean consumeMessage(Channel provider, WebSocket ws, Object message) throws IOException {
                    try {
                        int cnt = srvCount.incrementAndGet();
                        int cc = callCount.get();
                        int rc = respCount.get();
                        if (cnt > 0 && (cnt % 500) == 0 || cc > 0 && (cc % 500) == 0 || rc > 0 && (rc % 500) == 0 || cc == cnt) {
                            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "][" + cc + "/" + cnt + "/" + rc + "][" + ((SocketChannel) provider).getRemoteAddress() + "].server: " + ((message instanceof String) ? ((String) message).length() : ((byte[]) message).length));
                        }
                        List list = decode.readObject((message instanceof String) ? new StringReader((String) message) : new InputStreamReader(new ByteArrayInputStream((byte[]) message), "UTF-8"), List.class);
                        //System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "][" + ((SocketChannel) provider).getRemoteAddress() + "].server: " + list);
                        String s = encode.writeObject(list);
                        //NetTools.delay((long) (Math.random() * 16));
                        ws.send(s);
                    } catch (Throwable th) {
                        System.err.println("[" + System.currentTimeMillis() + "]ERROR[" + Thread.currentThread().getName() + "][" + ((SocketChannel) provider).getRemoteAddress() + "].server: " + ((message instanceof String) ? ((String) message).length() : ((byte[]) message).length));
                        th.printStackTrace();
                    } finally {
                        return true;
                    }
                }
            };
            ws.filter(drS);

            int wsPort = 18003;
            TCPHandler wsh = new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort)).defaultHandler(ws);
            TCPHandler wsc = new TCPHandler().defaultHandler(ws);

            cs.add(wsh);
            cs.add(wsc);

            DI_WS dic = new DI_WS() {
                JSON.Encoder encode = new JSON.Encoder();
                JSON.Decoder decode = new JSON.Decoder();

                @Override
                public WebSocket createWebSocket(Channel provider, boolean client, WebSocketAddons addOns) {
                    WebSocket r = super.createWebSocket(provider, client, addOns);
                    if (r == null) {
                        try {
                            r = new WebSocketChannel(new WebSocketAddons().addExtensions(WebSocket.allExtensions.values().toArray(new WebSocketExtension[WebSocket.allExtensions.size()])), (SocketChannel) provider);
                            r.setDefaultMessageListener(new BufferingMessageListener());
                            if (r instanceof WebSocketChannel) {
                                ((WebSocketChannel) r).DUMP = true;
                            }
                            //r.setFrameMonitor(fm);
                            r.handshake("0.1", "/ws", "localhost:" + wsPort, "local", null, null, 13, null);
                        } catch (IOException ioex) {
                            try {
                                provider.close();
                            } catch (Throwable th) {
                            }
                        }
                    }
                    return r;
                }

                @Override
                public boolean consumeMessage(Channel provider, WebSocket ws, Object message) throws IOException {
                    try {
                        respCount.incrementAndGet();
                        List list = decode.readObject((message instanceof String) ? new StringReader((String) message) : new InputStreamReader(new ByteArrayInputStream((byte[]) message), "UTF-8"), List.class);
                        //System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "][" + ((SocketChannel) provider).getLocalAddress() + "].client: " + list);
                    } catch (Throwable th) {
                        System.err.println("[" + System.currentTimeMillis() + "]ERROR[" + Thread.currentThread().getName() + "][" + ((SocketChannel) provider).getLocalAddress() + "].client");
                        th.printStackTrace();
                    } finally {
                        return true;
                    }
                }
            };
            dic.filter(drC);

            InetSocketAddress wsAddr = new InetSocketAddress(InetAddress.getByName("localhost"), wsPort);
            SocketChannel[] chs = new SocketChannel[20];
            for (int i = 0; i < chs.length; i++) {
                chs[i] = wsc.connect(wsAddr, dic);
            }

            CommonTools.wait(1000 * 10, () -> {
                for (int i = 0; i < chs.length; i++) {
                    if (dic.websocket(chs[i]) == null || !dic.websocket(chs[i]).isInitialized()) {
                        return true;
                    }
                }
                return false;
            });

            for (SocketChannel ch : chs) {
                final WebSocket w = dic.websocket(ch);
                Thread run = new Thread() {
                    @Override
                    public void run() {
                        try {
                            setName("Run: " + ch.getLocalAddress());
                            for (int k = 0; k < 1; k++) {
                                for (int i = 10; i < 6150; i++) {
                                    char[] chrs = new char[i];
                                    Arrays.fill(chrs, '0');
                                    char[] chl = ("" + i + " ").toCharArray();
                                    for (int j = 0; j < chl.length; j++) {
                                        chrs[j] = chl[j];
                                    }

                                    for (int j = Math.min(20, chl.length + 3); j < chrs.length; j++) {
                                        chrs[j] = (char) (((int) 'A') + ((int) (Math.random() * 24)));
                                    }
                                    w.send("[\"" + new String(chrs) + "\"]");
                                    callCount.incrementAndGet();
                                    //NetTools.delay((long) (Math.random() * 10));
                                }
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        } finally {
                            System.err.println("STOPPED " + getName());
                        }
                    }
                };
                run.setDaemon(true);
                run.start();
            }

//            for (int i = 10; i < 15; i++) {
//                for (SocketChannel ch : chs) {
//                    WebSocket w = dic.websocket(ch);
//                    char[] chrs = new char[i];
//                    Arrays.fill(chrs, '0');
//                    char[] chl = ("" + i + " ").toCharArray();
//                    for (int j = 0; j < chl.length; j++) {
//                        chrs[j] = chl[j];
//                    }
//                    w.send("[\"" + new String(chrs) + "\"]");
//                }
//            }
            System.out.println("------------------------ wait 30 sec.: " + callCount.get() + "/" + srvCount.get() + "/" + respCount.get());
            NetTools.delay(1000 * 30);
        } finally {
            System.out.println("------------------------ stopping: " + callCount.get() + "/" + srvCount.get() + "/" + respCount.get());
            cs.stop();

            if (fmChannel != null) {
                try {
                    flush(fmChannel, fmCache);
                    fmChannel.close();
                } catch (Throwable th) {
                }
            }
        }
        System.out.println("------------------------ done: " + callCount.get() + "/" + srvCount.get() + "/" + respCount.get());
        System.out.println("------------------------ Extension counters:"
                + "\n  timestamp: " + WebSocketExtensionTimestamp.getPrepareCount() + " / " + WebSocketExtensionTimestamp.getRestoreCount()
                + "\n  gzip     : " + WebSocketExtensionGZip.getPrepareCount() + " / " + WebSocketExtensionGZip.getRestoreCount()
        );
    }
}
