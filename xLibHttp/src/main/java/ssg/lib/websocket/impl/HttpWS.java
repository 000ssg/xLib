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

import ssg.lib.http.base.HttpData;
import ssg.lib.websocket.WebSocket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.websocket.WebSocketProcessor;
import ssg.lib.websocket.WebSocketProcessor.WebSocketMessageListener;

/**
 *
 * @author 000ssg
 */
public class HttpWS extends HttpData {

    WebSocket ws;
    HttpMatcher matcher;

    public HttpWS(WebSocket ws) {
        this.ws = ws;
        matcher = new HttpMatcher(ws.getPath());
        setFlags(HF_SWITCHED);
    }

    public HttpWS(WebSocket ws, HttpData http) throws IOException {
        super(http);
        getHead().setHeader(HH_UPGRADE, null);
        this.ws = ws;
        if (http instanceof HttpRequest) {
            matcher = new HttpMatcher(http.getHead().getProtocolInfo()[1]);
        }
        setFlags(HF_SWITCHED);
    }

    @Override
    public HttpMatcher getMatcher() {
        return matcher;
    }

    @Override
    public void onHeaderLoaded() {
    }

    @Override
    public List<ByteBuffer> get() throws IOException {
        return ws.get();
    }

    @Override
    public long add(Collection<ByteBuffer>... bufs) throws IOException {
        return ws.add(bufs);
    }

    @Override
    public void cancel() {
        super.cancel();
        try {
            ws.closeConnection();
        } catch (Throwable th) {
        }
    }

    public void setWebSocketProcessor(WebSocketProcessor wsp) {
        if (wsp != null) {
            ws.setProcessor(wsp);
        }
    }

    public void setWebSocketMessageListener(WebSocketMessageListener l) {
        if (l != null) {
            ws.setDefaultMessageListener(l);
        }
    }

    public WebSocket getWebSocket() {
        return ws;
    }

    @Override
    public boolean isCompleted() {
        return !ws.isConnected();
    }

}
