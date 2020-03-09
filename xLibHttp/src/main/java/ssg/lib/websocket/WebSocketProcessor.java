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
package ssg.lib.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import ssg.lib.common.net.NetTools;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class WebSocketProcessor {

    WebSocket ws;
    Thread process;
    List<WebSocketMessageListener> listeners = new ArrayList<>();
    WebSocketMessageListener[] ls = new WebSocketMessageListener[0];
    int fc = 0;
    public boolean DUMP_FRAMES = false;

    public WebSocketProcessor(WebSocket webSocket) {
        this.ws = webSocket;
        initProcessor();
    }

    public WebSocketProcessor(WebSocket webSocket, boolean async) {
        this.ws = webSocket;
        if (!async) {
            initProcessor();
        }
    }

    void initProcessor() {
        Thread cl = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("WSP-" + ws.toString());
                try {
                    while (ws.isConnected()) {
                        ws.fetch();
                        processCycle();
                        NetTools.delay(1);
                    }
                } catch (AsynchronousCloseException acex) {
                    int a = 0;
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                process = null;
            }
        };
        cl.setDaemon(true);
        cl.start();
        process = cl;
    }

    public void processCycle() throws IOException {
        Object msg = null;
        while ((msg = ws.nextMessage()) != null) {
            processMessage(msg);
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public void stop() {
        if (isRunning()) {
            process.interrupt();
        }
        process = null;
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////// ping-pong error handling
    ////////////////////////////////////////////////////////////////////////////
    private long pingTime;

    public boolean onPing() {
        if (pingTime == 0) {
            pingTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public void onPong(byte[] pong) {
        if (ls != null && ls.length > 0) {
            Long delay = (pingTime != 0) ? System.currentTimeMillis() - pingTime : null;
            for (WebSocketMessageListener l : ls) {
                l.onPong(ws, pong, delay);
            }
        }
        pingTime = 0;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public void onReceivedFrame(WebSocketFrame frame) {
        if (DUMP_FRAMES) {
            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "]:pre [" + getClass().getSimpleName() + "] got frame: " + frame);
        }
    }

    public void onHandledFrame(WebSocketFrame frame) {
        if (DUMP_FRAMES) {
            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "]:post[" + getClass().getSimpleName() + "] got frame: " + frame);
        }
    }

    public void processMessage(Object message) {
        try {
            String text = null;
            byte[] data = null;
            if (message instanceof String) {
                text = (String) message;
            } else if (message instanceof CharBuffer) {
                text = ((CharBuffer) message).toString();
            } else if (message instanceof CharBuffer[]) {
                text = BufferTools.toText((CharBuffer[]) message);
            } else if (message instanceof ByteBuffer) {
                ByteBuffer bb = (ByteBuffer) message;
                data = new byte[bb.remaining()];
                bb.get(data);
            } else if (message instanceof ByteBuffer[]) {
                data = BufferTools.toBytes(false, (ByteBuffer[]) message);
            } else if (message instanceof Reader) {
                List<CharBuffer> chs = BufferTools.toCharBuffers((Reader) message);
                text = BufferTools.toText(chs);
            } else if (message instanceof byte[]) {
                data = (byte[]) message;
            } else if (message instanceof InputStream) {
                List<ByteBuffer> bbs = BufferTools.toByteBuffers((InputStream) message);
                data = BufferTools.toBytes(false, bbs);
            } else {
                processUnsupportedMessageData(message);
            }

            if (text != null || data != null) {
                if (ws.getProtocolHandler() != null) {
                    ws.getProtocolHandler().onConsume(ws.getConnection(), ws, message);
                } else {
                    for (WebSocketMessageListener l : ls) {
                        try {
                            l.onMessage(ws, text, data);
                        } catch (Throwable th) {
                            onProcessError(l, th, text, data, message);
                        }
                    }
                }
            } else {
                onProcessError(null, null, text, data, message);
            }

        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void processUnsupportedMessageData(Object message) {
        throw new RuntimeException("Unsupported message data: " + message);
    }

    public void onProcessError(WebSocketMessageListener l, Throwable th, String text, byte[] data, Object message) {
    }

    public void addWebSocketMessageListener(WebSocketMessageListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
            l.onEstablished(ws);
            ls = listeners.toArray(new WebSocketMessageListener[listeners.size()]);
        }
    }

    public void removeWebSocketMessageListener(WebSocketMessageListener l) {
        if (l != null && listeners.contains(l)) {
            listeners.remove(l);
            l.onStopped(ws);
            ls = listeners.toArray(new WebSocketMessageListener[listeners.size()]);
        }
    }

    public static interface WebSocketMessageListener {

        void onEstablished(WebSocket ws);

        void onStopped(WebSocket ws);

        void onMessage(WebSocket ws, String text, byte[] data);

        void onPong(WebSocket ws, byte[] pong, Long delay);
    }

    public static class PongMessageListener implements WebSocketMessageListener {

        byte[] pong;
        Long delay;

        @Override
        public void onEstablished(WebSocket ws) {
        }

        @Override
        public void onStopped(WebSocket ws) {
        }

        @Override
        public void onMessage(WebSocket ws, String text, byte[] data) {
        }

        @Override
        public void onPong(WebSocket ws, byte[] pong, Long delay) {
            this.pong = pong;
            this.delay = delay;
        }

        public boolean hasPong() {
            return pong != null;
        }

        public byte[] fetchPong() {
            if (pong != null) {
                try {
                    return pong;
                } finally {
                    pong = null;
                }
            }
            return null;
        }
    }

    public static class DumpMessageListener implements WebSocketMessageListener {

        public String formatText(String text) {
            return text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\f", "\\f");
        }

        public String formatData(byte[] data) {
            return BufferTools.dump(data);
        }

        @Override
        public void onEstablished(WebSocket ws) {
            System.out.println("ESTABLISHED [" + Thread.currentThread().getName() + "][" + ws.getClass().getSimpleName() + "]");
        }

        @Override
        public void onStopped(WebSocket ws) {
            System.out.println("STOPPED [" + Thread.currentThread().getName() + "][" + ws.getClass().getSimpleName() + "]");
        }

        @Override
        public void onMessage(WebSocket ws, String text, byte[] data) {
            try {
                if (text != null) {
                    System.out.println("DUMP [" + Thread.currentThread().getName() + "][" + ws.getClass().getSimpleName() + "] TEXT: " + formatText(text));
                } else {
                    System.out.println("DUMP [" + Thread.currentThread().getName() + "][" + ws.getClass().getSimpleName() + "] BYTE: " + formatData(data));
                }
            } catch (Throwable th) {
                System.out.println("DUMP [" + Thread.currentThread().getName() + "][" + ws.getClass().getSimpleName() + "] ERROR: " + th);
            }
        }

        @Override
        public void onPong(WebSocket ws, byte[] pong, Long delay) {
            System.out.println("DUMP [" + Thread.currentThread().getName() + "][" + ws.getClass().getSimpleName() + "] PONG[" + delay + "]: " + formatData(pong));
        }

    }

    public static class BufferingMessageListener implements WebSocketMessageListener {

        DateFormat dtf = new SimpleDateFormat("dd-MM-yy HH:mm:ss.SSS");

        List messages = new ArrayList();
        int eventCount;
        int textCount;
        int binCount;
        long textLen;
        long binLen;
        int nothingCount;
        long firstEventTime;
        long lastEventTime;
        long firstTextEventTime;
        long lastTextEventTime;
        long firstBinEventTime;
        long lastBinEventTime;
        long firstNothingEventTime;
        long lastNothingEventTime;

        @Override
        public void onMessage(WebSocket ws, String text, byte[] data) {
            eventCount++;
            if (text != null) {
                messages.add(text);
                textCount++;
                textLen += text.length();
            } else if (data != null) {
                messages.add(data);
                binCount++;
                binLen += data.length;
            } else {
                nothingCount++;
            }
        }

        public int getMessagesCount() {
            return messages.size();
        }

        public List removeMessages() {
            synchronized (messages) {
                List r = new ArrayList(messages.size());
                r.addAll(messages);
                messages.clear();
                return r;
            }
        }

        @Override
        public void onEstablished(WebSocket ws) {
        }

        @Override
        public void onStopped(WebSocket ws) {
        }

        @Override
        public void onPong(WebSocket ws, byte[] pong, Long delay) {
            // ignored
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName());
            sb.append('{');
            sb.append("\n  messages: " + this.getMessagesCount() + "/" + eventCount + ((firstEventTime != 0) ? "  " + dtf.format(firstEventTime) + "  -  " + dtf.format(lastEventTime) : ""));
            if (textCount > 0) {
                sb.append("\n      text: " + textCount + " [" + textLen + "]" + ((firstTextEventTime != 0) ? "  " + dtf.format(firstTextEventTime) + "  -  " + dtf.format(lastTextEventTime) : ""));
            }
            if (binCount > 0) {
                sb.append("\n    binary: " + binCount + " [" + binLen + "]" + ((firstBinEventTime != 0) ? "  " + dtf.format(firstBinEventTime) + "  -  " + dtf.format(lastBinEventTime) : ""));
            }
            if (nothingCount > 0) {
                sb.append("\n   nothing: " + nothingCount + ((firstNothingEventTime != 0) ? "  " + dtf.format(firstNothingEventTime) + "  -  " + dtf.format(lastNothingEventTime) : ""));
            }
            sb.append('\n');
            sb.append('}');
            return sb.toString();
        }

    }
}
