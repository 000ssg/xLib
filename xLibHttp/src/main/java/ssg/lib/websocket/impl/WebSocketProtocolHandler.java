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

import java.io.IOException;
import java.nio.channels.Channel;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocket.WebSocketAddons;

/**
 * WebSocket protocol-specific handler
 */
public interface WebSocketProtocolHandler {

    boolean canInitialize(Channel provider, WebSocket ws);

    void initialize(Channel provider, WebSocket ws);

    void delete(Channel provider, WebSocket ws);

    WebSocketAddons prepareAddons(Channel provider, boolean client, WebSocketAddons addOns) throws IOException;

    boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException;

    boolean onProduce(Channel provider, WebSocket ws) throws IOException;

    /**
     * Check if can accept data.
     *
     * @param provider
     * @param ws
     * @return
     * @throws IOException
     */
    boolean isReady(Channel provider, WebSocket ws) throws IOException;

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////// lifecycle support
    ////////////////////////////////////////////////////////////////////////
    void onStarted(Object... parameters);

    void onStop(Object... parameters);

}
