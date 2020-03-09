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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class WebSocketExtensionGZip implements WebSocketExtension {

    static final long serialVersionUID = 1L;
    static final AtomicInteger prepareCount = new AtomicInteger();
    static final AtomicInteger restoreCount = new AtomicInteger();

    @Override
    public String getName() {
        return "gzipped";
    }

    @Override
    public Collection<String> getParameterNames() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public <T> T getParameter(String name) {
        return null;
    }

    @Override
    public void setParameter(String name, Object value) {
    }

    @Override
    public int size(WebSocketFrame frame, byte[] data, int off) {
        return 0;
    }

    @Override
    public byte[] prepareFrame(WebSocketFrame frame, byte[] data, int off) throws IOException {
        prepareCount.incrementAndGet();
        //if (frame.getLength() > 125) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);
        gos.write(data, off, (int) frame.getLength());
        gos.flush();
        gos.close();
        baos.close();
        byte[] buf = baos.toByteArray();
        if (buf.length < frame.getLength()) {
            frame.setReserved2(true);
            frame.setLength(buf.length);
            data = buf;
        }
        //}
        return data;
    }

    @Override
    public byte[] restoreFrame(WebSocketFrame frame, byte[] data) throws IOException {
        if (data != null && frame.getLength() > 0 && frame.isReserved2()) {
            restoreCount.incrementAndGet();
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            GZIPInputStream gos = new GZIPInputStream(bais);
            byte[] buf = BufferTools.toBytes(false, BufferTools.toByteBuffers(gos));
            frame.registerExtensionData(this, data.length, buf.length);
            data = buf;
        }
        return data;
    }

    @Override
    public boolean handleExtensionFrame(WebSocket ws, WebSocketFrame frame, ByteBuffer... payload) throws IOException {
        return false;
    }

    @Override
    public WebSocketExtension clone() {
        try {
            return (WebSocketExtension) super.clone();
        } catch (CloneNotSupportedException cnsex) {
            cnsex.printStackTrace();
            return null;
        }
    }

    public static int getPrepareCount() {
        return prepareCount.get();
    }

    public static int getRestoreCount() {
        return restoreCount.get();
    }
}
