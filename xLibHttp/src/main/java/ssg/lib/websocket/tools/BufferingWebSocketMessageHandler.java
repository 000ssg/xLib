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
package ssg.lib.websocket.tools;

import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketFrame;
import ssg.lib.websocket.WebSocketMessageHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.websocket.WebSocket.FrameMonitor;

/**
 *
 * @author 000ssg
 */
public class BufferingWebSocketMessageHandler implements WebSocketMessageHandler {

    byte[] buf = new byte[1024 * 30];
    List<byte[]> buffer = new ArrayList<>();
    byte optcode;
    byte[] m;
    // current frame processing...
    long len = 0;
    int off = 0;

    @Override
    public void onMessageStart(WebSocketFrame frame) throws IOException {
        optcode = frame.getOpCode();
        m = frame.getMask();
        buffer.clear();
        len = frame.getLength();
        off = 0;
    }

    @Override
    public void onMessageContinue(WebSocket ws, WebSocketFrame frame, ByteBuffer... is) throws IOException {
        m = frame.getMask();
        len=frame.getLength();
        while (len > 0) {
            int c = (int) BufferTools.readTo(is, buf, 0, Math.min((int) len, buf.length));
            if (c == -1) {
                throw new IOException("Unexpected EOF detected. Unread " + len + " bytes.");
            } else if (c == 0) {
                // WIP, next data should be added...
                break;
            } else {
                if (m != null) {
                    for (int i = 0; i < c; i++) {
                        buf[i] ^= m[(i + off) % 4];
                    }
                }
                byte[] bb = Arrays.copyOf(buf, c);
                if (m != null && ws.getFrameMonitor() != null && ws.getFrameMonitor().check(FrameMonitor.IF_UNMASKED, ws, frame, null)) {
                    ws.getFrameMonitor().onCompletedFrame(ws, frame, null, new ByteBuffer[]{ByteBuffer.wrap(bb)});
                }
                {//if (off == 0) {
                    int xsz = ws.getWebSocketExtensions().size(frame, bb);
                    if (xsz > 0 && xsz <= c) {
                        bb = ws.getWebSocketExtensions().unapplyExtensions(frame, bb);
                    }
                }
                buffer.add(bb);
                len -= c;
                off += c;
            }
        }
    }

    @Override
    public Object onMessageCompleted(WebSocket ws, WebSocketFrame frame, ByteBuffer... is) throws IOException {
        onMessageContinue(ws, frame, is);
        // return null if not all data are read for final frame...
        if (len > 0) {
            return null;
        }
        try {
            if (optcode == WebSocketFrame.OP_TEXT) {
                return BufferTools.toText("UTF-8", BufferTools.merge(buffer));
            } else if (optcode == WebSocketFrame.OP_BINARY) {
                return BufferTools.merge(buffer);
            } else {
                return null;
            }
        } finally {
            buffer.clear();
            optcode = -1;
        }
    }

}
