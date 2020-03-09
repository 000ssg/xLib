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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.common.ByteArray;

/**
 *
 * @author 000ssg
 */
public class WebSocketExtensionTimestamp implements WebSocketExtension {

    static final long serialVersionUID = 1L;
    static final AtomicInteger prepareCount = new AtomicInteger();
    static final AtomicInteger restoreCount = new AtomicInteger();
    boolean keepOffset = false;

    @Override
    public String getName() {
        return "timestamp";
    }

    @Override
    public Collection<String> getParameterNames() {
        return Collections.singletonList("keepOffset");
    }

    @Override
    public <T> T getParameter(String name) {
        if ("keepOffset".equals(name)) {
            return (T) (Boolean) keepOffset;
        }
        return null;
    }

    @Override
    public void setParameter(String name, Object value) {
        if ("keepOffset".equals(name)) {
            if (value instanceof Boolean) {
                keepOffset = (Boolean) value;
            } else if ("true".equalsIgnoreCase("" + value)) {
                keepOffset = true;
            } else if ("1".equalsIgnoreCase("" + value)) {
                keepOffset = true;
            } else {
                keepOffset = false;
            }
        }
    }

    @Override
    public int size(WebSocketFrame frame, byte[] data, int off) {
        return (frame == null || frame.isReserved3()) ? 8 + ((keepOffset) ? 4 : 0) : 0;
    }

    @Override
    public byte[] prepareFrame(WebSocketFrame frame, byte[] data, int dataOff) {
        prepareCount.incrementAndGet();
        long ts = System.currentTimeMillis();
        int off = TimeZone.getDefault().getOffset(ts);
        int add = size(null, data, dataOff);
        // cannot extend control ops if data will exceed min allowed.
        if (((frame.getOpCode() & 0x8) != 0) && data != null && data.length + add > 125) {
            return data;
        }
        if (data != null) {
            byte[] buf = new byte[data.length + add];
            System.arraycopy(data, 0, buf, add, data.length);
            data = buf;
        } else {
            data = new byte[add];
        }
        ByteArray ba = new ByteArray(data);
        ba.setLong(0, ts);
        if (keepOffset) {
            ba.setInt(8, off);
        }
        frame.setLength(data.length);
        frame.setReserved3(true);
        return data;
    }

    @Override
    public byte[] restoreFrame(WebSocketFrame frame, byte[] data) {
        restoreCount.incrementAndGet();
        int add = size(frame, data, 0);
        if (add > 0) {
            ByteArray ba = new ByteArray(data);
            long ts = ba.getLong(0);
            int off = (keepOffset) ? ba.getInt(8) : -1;
            frame.registerExtensionData(this, ts, off);
            if (frame.getLength() == add) {
                return null;
            } else {
                return Arrays.copyOfRange(data, add, data.length);
            }
        } else {
            return data;
        }
    }

    @Override
    public boolean handleExtensionFrame(WebSocket ws, WebSocketFrame frame, ByteBuffer... payload) throws IOException {
        return false;
    }

    @Override
    public WebSocketExtensionTimestamp clone() {
        try {
            return (WebSocketExtensionTimestamp) super.clone();
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
