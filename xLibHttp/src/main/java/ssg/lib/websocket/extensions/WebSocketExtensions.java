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
package ssg.lib.websocket.extensions;

import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketExtension;
import ssg.lib.websocket.WebSocketFrame;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.websocket.WebSocket.FrameMonitor;

/**
 *
 * @author 000ssg
 */
public class WebSocketExtensions {

    Map<String, WebSocketExtension> extensions = new LinkedHashMap<String, WebSocketExtension>();
    List<V2<WebSocketFrame, byte[]>> requests = Collections.synchronizedList(new ArrayList<V2<WebSocketFrame, byte[]>>());
    private FrameMonitor frameMonitor;

    public WebSocketExtensions() {
    }

    public void add(WebSocketExtension ext) {
        extensions.put(ext.getName(), ext);
    }

    public byte[] applyExtensions(WebSocketFrame frame, byte[] data, int off) throws IOException {
        for (WebSocketExtension wse : extensions.values()) {
            byte[] buf = wse.prepareFrame(frame, data, off);
            if (buf != data) {
                off = 0;
            }
            data = buf;
            if (frameMonitor != null && frameMonitor.check(FrameMonitor.X_APPLIED, null, frame, wse)) {
                frameMonitor.onOutgoingFrame(null, frame, wse, new ByteBuffer[]{ByteBuffer.wrap(data)}, off);
            }
        }
        return data;
    }

    public byte[] unapplyExtensions(WebSocketFrame frame, byte[] data) throws IOException {
        if (!extensions.isEmpty()) {
            WebSocketExtension[] wses = extensions.values().toArray(new WebSocketExtension[extensions.size()]);
            for (int i = wses.length - 1; i >= 0; i--) {
                WebSocketExtension wse = wses[i];
                data = wse.restoreFrame(frame, data);
                if (frameMonitor != null && frameMonitor.check(FrameMonitor.X_UNAPPLIED, null, frame, wse)) {
                    frameMonitor.onCompletedFrame(null, frame, wse, new ByteBuffer[]{ByteBuffer.wrap(data)});
                }
            }
        }
        return data;
    }

    public boolean handleExtensionFrame(WebSocket ws, WebSocketFrame frame) throws IOException {
        if (!extensions.isEmpty()) {
            WebSocketExtension[] wses = extensions.values().toArray(new WebSocketExtension[extensions.size()]);
            for (int i = wses.length - 1; i >= 0; i--) {
                WebSocketExtension wse = wses[i];
                if (wse.handleExtensionFrame(ws, frame)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int size(WebSocketFrame frame, byte[] data) {
        int r = 0;
        for (WebSocketExtension wse : extensions.values()) {
            int sz = wse.size(frame, data, r);
            r += sz;
        }
        return r;
    }

    public boolean hasExtensionFrames() {
        return !requests.isEmpty();
    }

    public V2<WebSocketFrame, byte[]> nextExtensionFrame() {
        return (!requests.isEmpty()) ? requests.remove(0) : null;
    }

    public void registerExtensionFrame(WebSocketFrame frame, byte[] data) {
        if (frame != null) {
            requests.add(new V2<WebSocketFrame, byte[]>(frame, data));
        }
    }

    /**
     * @return the frameMonitor
     */
    public FrameMonitor getFrameMonitor() {
        return frameMonitor;
    }

    /**
     * @param frameMonitor the frameMonitor to set
     */
    public void setFrameMonitor(FrameMonitor frameMonitor) {
        this.frameMonitor = frameMonitor;
    }
}
