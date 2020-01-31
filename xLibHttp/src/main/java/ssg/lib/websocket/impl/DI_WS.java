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
package ssg.lib.websocket.impl;

import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor.BufferingMessageListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.base.BaseDI;
import ssg.lib.websocket.WebSocket.WebSocketAddons;

/**
 *
 * @author 000ssg
 */
public class DI_WS<P extends Channel> extends BaseDI<ByteBuffer, P> {

    Collection<WebSocketProtocolHandler> protocols = new LinkedHashSet<>();
    Map<P, WebSocket> webSockets = new HashMap<>();
    Map<P, WebSocketHandshakeListener> pendingClients = new HashMap<>();

    private int maxMessagesInInputQueue = 10;
    private long maxMessagesSizeInInputQueue = 200000;

    public DI_WS() {
    }

    public DI_WS addWebSocketProtocolHandler(WebSocketProtocolHandler... protocolHandlers) {
        if (protocolHandlers != null) {
            for (WebSocketProtocolHandler ph : protocolHandlers) {
                if (ph != null && !protocols.contains(ph)) {
                    protocols.add(ph);
                }
            }
        }
        return this;
    }

    public DI_WS removeWebSocketProtocolHandler(WebSocketProtocolHandler... protocolHandlers) {
        if (protocolHandlers != null) {
            for (WebSocketProtocolHandler ph : protocolHandlers) {
                if (ph != null && protocols.contains(ph)) {
                    protocols.remove(ph);
                }
            }
        }
        return this;
    }

    public WebSocketProtocolHandler[] getWebSocketProtocolHandler() {
        return protocols.toArray(new WebSocketProtocolHandler[protocols.size()]);
    }

    public WebSocket websocket(P provider) {
        return webSockets.get(provider);
    }

    @Override
    public void healthCheck(P provider) throws IOException {
        WebSocket ws = websocket(provider);
        if (ws != null && ws.isConnected() && ws.isInitialized()) {
            ws.pong(("DI_WS healthcheck " + provider).getBytes());
        }
        super.healthCheck(provider);
    }

    @Override
    public boolean isReady(P provider) throws IOException {
        WebSocket ws = websocket(provider);
        if (ws == null || !ws.hasNextMessage()) {
            return true;
        }
        if (getMaxMessagesInInputQueue() > 0 && ws.getNextMessagesCount() > getMaxMessagesInInputQueue()) {
            return false;
        } else if (getMaxMessagesSizeInInputQueue() > 0 && ws.getNextMessagesSizeEstimate() > getMaxMessagesSizeInInputQueue()) {
            return false;
        } else if (ws.getProtocolHandler() != null && !ws.getProtocolHandler().isReady(provider, ws)) {
            return false;
        }
        return true;
    }

    @Override
    public long size(Collection<ByteBuffer>... data) {
        return BufferTools.getRemaining(data);
    }

    @Override
    public void consume(P provider, Collection<ByteBuffer>... bufs) throws IOException {
        long c = BufferTools.getRemaining(bufs);
        //provider = ensureProvider(provider);
        WebSocket ws = websocket(provider);
        if (c == 0 && ws == null) {
            return;
        }

        // init server-side WebSocket, if possible.
        if (ws == null && c > 0) {
            ws = createWebSocket(provider, false, null);
            if (ws == null) {
                return;
            }
            webSockets.put(provider, ws);
        }

        if (!ws.isInitialized() && bufs != null) {
            if (!ws.isInitialized()) {
                for (Collection<ByteBuffer> bbs : bufs) {
                    ws.add(bbs);
                    if (ws.isInitialized()) {
                        onInitializedWS(provider, ws);
                        break;
                    }
                }
            }
        }
        if (ws.isInitialized()) {
            ws.add(bufs);
        }

        if (ws.getDefaultMessageListener() instanceof BufferingMessageListener) {
            BufferingMessageListener bml = (BufferingMessageListener) ws.getDefaultMessageListener();
            if (bml.getMessagesCount() > 0) {
                List lst = bml.removeMessages();
                if (lst != null) {
                    for (Object message : lst) {
                        if (checkMessage(ws, message)) {
                            if (ws.getProtocolHandler() != null) {
                                ws.getProtocolHandler().onConsume(provider, ws, message);
                            } else {
                                consumeMessage(provider, ws, message);
                            }
                        }
                    }
                }
            }
        }

    }

    @Override
    public List<ByteBuffer> produce(P provider) throws IOException {
        //provider = ensureProvider(provider);
        WebSocket ws = websocket(provider);

        if (ws == null) {
            ws = createWebSocket(provider, true, null);
            if (ws != null) {
                webSockets.put(provider, ws);
            }
        }

        if (ws != null) {
            boolean wsInitialized = ws.isInitialized();
            try {
                if (ws.getProtocolHandler() != null) {
                    ws.getProtocolHandler().onProduce(provider, ws);
                }

                return ws.get();
            } finally {
                if (!wsInitialized && ws.isInitialized()) {
                    this.onInitializedWS(provider, ws);
                }
            }
        } else {
            return null;
        }
        //return super.get(provider);
    }

    public void addPendingWebSocketClientHandshake(P provider, WebSocketHandshakeListener wshl) {
        if (provider != null && wshl != null) {
            pendingClients.put(provider, wshl);
        }
    }

    public WebSocket createWebSocket(P provider, boolean client, WebSocketAddons addOns) {
        WebSocket ws = null;
        if (client) {
            WebSocketHandshakeListener wshl = pendingClients.remove(provider);
            if (wshl != null) {
                ws = new WebSocketChannel(prepareWebSocketAddons(provider, client, addOns), (SocketChannel) provider);
                ws.setDefaultMessageListener(new BufferingMessageListener());
                try {
                    wshl.onWebSocketClientHandshake(provider, ws);
                } catch (Throwable th) {
                    th.printStackTrace();
                    try {
                        delete(provider);
                    } catch (IOException ioex) {
                    }
                }
            }
        } else {
            ws = new WebSocketChannel(prepareWebSocketAddons(provider, client, addOns), (SocketChannel) provider);
            ws.setDefaultMessageListener(new BufferingMessageListener());
        }
        return ws;
    }

    public WebSocketAddons prepareWebSocketAddons(P provider, boolean client, WebSocketAddons r) {
        for (WebSocketProtocolHandler wsh : protocols) {
            if (wsh == null) {
                continue;
            }
            try {
                r = wsh.prepareAddons(provider, client, r);
            } catch (IOException ioex) {
                ioex.printStackTrace();
            }
        }
        return r;
    }

    public void onInitializedWS(P provider, WebSocket ws) {
        boolean initialized = false;
        if (protocols.isEmpty()) {
            initialized = true;
        } else {
            for (WebSocketProtocolHandler wsh : protocols) {
                if (wsh == null) {
                    continue;
                }
                if (wsh.canInitialize(provider, ws)) {
                    wsh.initialize(provider, ws);
                    initialized = true;
                    ws.setProtocolHandler(wsh);
                }
            }
        }

        if (!initialized) {
            try {
                this.delete(provider);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    /**
     * Simple entry point to validate messages. By default all messages are
     * dropped (returns false).
     *
     * @param ws
     * @param message
     * @return
     * @throws IOException
     */
    public boolean checkMessage(WebSocket ws, Object message) throws IOException {
        return message instanceof String || message instanceof byte[];
    }

    public boolean consumeMessage(P provider, WebSocket ws, Object message) throws IOException {
        return false;
    }

    @Override
    public void delete(P provider) throws IOException {
        WebSocket ws = websocket(provider);
        if (ws != null && ws.getProtocolHandler() != null) {
            ws.getProtocolHandler().delete(provider, ws);
        }

        // shutdown websocket
        if (ws != null) {
            try {
                if (ws.isConnected()) {
                    ws.closeConnection();
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        super.delete(provider);

        // TODO: ?????????????????????? DO WE NEED IT HERE ??????????
        // shutdown channel
        if (provider instanceof Channel) {
            Channel ch = (Channel) provider;
            if (ch.isOpen()) {
                ch.close();
            }
        }

    }

    public static interface WebSocketHandshakeListener<P extends Channel> {

        void onWebSocketClientHandshake(P provider, WebSocket ws) throws IOException;
    }

    /**
     * @return the maxMessagesInInputQueue
     */
    public int getMaxMessagesInInputQueue() {
        return maxMessagesInInputQueue;
    }

    /**
     * @param maxMessagesInInputQueue the maxMessagesInInputQueue to set
     */
    public void setMaxMessagesInInputQueue(int maxMessagesInInputQueue) {
        this.maxMessagesInInputQueue = maxMessagesInInputQueue;
    }

    /**
     * @return the maxMessagesSizeInInputQueue
     */
    public long getMaxMessagesSizeInInputQueue() {
        return maxMessagesSizeInInputQueue;
    }

    /**
     * @param maxMessagesSizeInInputQueue the maxMessagesSizeInInputQueue to set
     */
    public void setMaxMessagesSizeInInputQueue(long maxMessagesSizeInInputQueue) {
        this.maxMessagesSizeInInputQueue = maxMessagesSizeInInputQueue;
    }

}
