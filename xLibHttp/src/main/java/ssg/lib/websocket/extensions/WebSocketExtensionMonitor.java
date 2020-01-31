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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public class WebSocketExtensionMonitor implements WebSocketExtension {

    static final long serialVersionUID = 1L;

    byte opcode = 0xB;
    Map<String, Object> remote;
    Map<String, Object> local;

    @Override
    public String getName() {
        return "monitor";
    }

    @Override
    public <T> T getParameter(String name) {
        if ("opcode".equals(name)) {
            return (T) (Byte) opcode;
        }
        return null;
    }

    @Override
    public void setParameter(String name, Object value) {
        if ("opcode".equals(name)) {
            byte b = opcode;
            if (value instanceof Number) {
                b = ((Number) value).byteValue();
            } else if (value instanceof String) {
                try {
                    String s = (String) value;
                    s = s.toLowerCase();
                    if (s.startsWith("0x")) {
                        b = (byte) (Long.parseLong(s.substring(2), 16) & 0xff);
                    } else {
                        b = (byte) (Long.parseLong(s) & 0xff);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            if (b > 0x2 && b < 0x8 || b > 0xA && b <= 0xF) {
                opcode = b;
            }
        }
    }

    @Override
    public int size(WebSocketFrame frame, byte[] data, int off) {
        return 0;
    }

    @Override
    public byte[] prepareFrame(WebSocketFrame frame, byte[] data, int off) throws IOException {
        return data;
    }

    @Override
    public byte[] restoreFrame(WebSocketFrame frame, byte[] data) throws IOException {
        return data;
    }

    @Override
    public boolean handleExtensionFrame(WebSocket ws, WebSocketFrame frame, ByteBuffer... payload) throws IOException {
        if (opcode == frame.getOpCode() && frame.getLength() > 0) {
            byte[] data = new byte[(int) frame.getLength()];
            int c = frame.readPayload(data, 0, payload);
            if (c != frame.getLength()) {
                throw new IOException("Invalid date size: expected " + frame.getLength() + ", got " + c + ".");
            }
            data = ws.getWebSocketExtensions().unapplyExtensions(frame, data);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            try {
                Map<String, Object> map = (Map<String, Object>) ois.readObject();
                if (remote == null) {
                    remote = map;
                } else {
                    remote.clear();
                    remote.putAll(map);
                }
            } catch (ClassNotFoundException ex) {
                throw new IOException("Failed to restore monitor data: " + ex);
            }
            ois.close();
            return true;
        }
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

    public Map<String, Object> getRemote() {
        return remote;
    }

    public Map<String, Object> getLocal(boolean refresh) {
        if (local == null || refresh) {
            if (local != null) {
                local.clear();
            }
            local = new LinkedHashMap<String, Object>();
            Runtime rt = Runtime.getRuntime();
            // runtime
            local.put("availableProcessors", rt.availableProcessors());
            local.put("freeMemory", rt.freeMemory());
            local.put("maxMemory", rt.maxMemory());
            local.put("totalMemory", rt.totalMemory());
            // system
            local.put("timestamp", System.currentTimeMillis());
            local.put("timestampnano", System.nanoTime());
            local.put("properties", System.getProperties());
            local.put("environment", System.getenv());
            // process
            local.put("thread", "id=" + Thread.currentThread().getId() + "; priority=" + Thread.currentThread().getPriority() + "; name=" + Thread.currentThread().getName());
        }
        return local;
    }

    public byte[] prepareMonitoringFrame(WebSocketFrame frame) throws IOException {
        Map<String, Object> map = this.getLocal(true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(map);
        oos.close();
        byte[] data = baos.toByteArray();

        frame.setOpCode(opcode);
        frame.setLength(data.length);
        return data;
    }
}
