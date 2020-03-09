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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.ByteArray;
import ssg.lib.common.buffers.BufferTools;

/**
 * Represents WebSocket frame. Reads or writes frame header. Payload data should
 * be read/written additionally from/to related stream.
 */
public class WebSocketFrame {

    public static final byte OP_CONTINUATION = 0x0;
    public static final byte OP_TEXT = 0x1;
    public static final byte OP_BINARY = 0x2;
    public static final byte OP_CONNECTION_CLOSE = 0x8;
    public static final byte OP_PING = 0x9;
    public static final byte OP_PONG = 0xA;

    static Map<Byte, String> op_names = new HashMap<Byte, String>() {
        {
            for (Field f : WebSocketFrame.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    if (f.getName().startsWith("OP_")) {
                        try {
                            put(f.getByte(null), f.getName());
                        } catch (Throwable th) {
                        }
                    }
                }
            }
        }
    };

    ByteArray frame = new ByteArray(new byte[2]);
    long len;
    byte[] mask;
    Map<Object, Object[]> extensionData;
    // helpers
    ByteArray len2r = new ByteArray(new byte[2], 0, 2, false);
    ByteArray len2w = new ByteArray(new byte[2], 0, 2, false);
    ByteArray len8r = new ByteArray(new byte[8], 0, 8, false);
    ByteArray len8w = new ByteArray(new byte[8], 0, 8, false);

    public WebSocketFrame() {
    }

    public WebSocketFrame(ByteBuffer... bufs) throws IOException {
        read(Arrays.asList(bufs));
    }

    public WebSocketFrame(Collection<ByteBuffer> bufs) throws IOException {
        if (bufs != null && !(bufs instanceof List)) {
            List<ByteBuffer> lst = new ArrayList<>();
            lst.addAll(bufs);
            bufs = lst;
        }
        read((List) bufs);
    }

    public void clear() {
        frame = new ByteArray(new byte[2]);
        len = 0;
        mask = null;
    }

    /**
     * Reads web socket frame header + any header extensions.
     *
     * @param is
     * @throws IOException
     */
    public void read(List<ByteBuffer> is) throws IOException {
        synchronized (this) {
            clear();

            long c = BufferTools.readTo(is.toArray(new ByteBuffer[is.size()]), frame.data());
            if (c != frame.data().length) {
                throw new IOException("Incomplete frame options/length: got " + c + ", expected " + frame.data().length);
            }
            len = frame.getUIntBits(1, 0, 7);
            if (len == 127) {
                c = BufferTools.readTo(is, len8r.data());
                if (c != len8r.data().length) {
                    throw new IOException("Incomplete frame length: got " + c + ", expected " + len8r.data().length);
                }
                len = len8r.getUInt(0);
            } else if (len == 126) {
                c = BufferTools.readTo(is, len2r.data());
                if (c != len2r.data().length) {
                    throw new IOException("Incomplete frame length: got " + c + ", expected " + len2r.data().length);
                }
                len = len2r.getUShort(0);
            }
            if (hasMask()) {
                mask = new byte[4];
                ByteArray l0 = new ByteArray(mask);
                c = BufferTools.readTo(is, l0.data());
                if (c != l0.data().length) {
                    throw new IOException("Incomplete frame mask: got " + c + ", expected " + l0.data().length);
                }
            }
        }
    }

    /**
     * Writes web socket header plus optional header extensions (length, mask)
     * followed by payload.
     *
     * @param os
     * @throws IOException
     */
    public List<ByteBuffer> write(byte[] data, int off, boolean preserveData) throws IOException {
        synchronized (this) {
            List<ByteBuffer> r = new ArrayList<ByteBuffer>();
            ByteBuffer out = ByteBuffer.allocateDirect(0
                    + 2 // flags + mask + size/size indicator
                    + ((len < 126) ? 0 : (len < 0x8FFF) ? 2 : 8) // data size + optional extra size
                    + ((hasMask() ? 4 : 0)) // mask if any
                    + (int) len // data size + optional extra size
            );

            out.put(frame.data());
            if (len > 125) {
                if (len < 0x8FFF) {
                    len2w.setShort(0, (short) (len & 0xFFFF));
                    out.put(len2w.data());
                } else {
                    len8w.setLong(0, len);
                    out.put(len8w.data());
                }
            }
            if (hasMask() && mask != null) {
                out.put(mask);
            }
            if (data != null && getLength() > 0) {
                writePayload(data, off, preserveData, out);
            }
            ((Buffer) out).flip();
            r.add(out);

            return r;
        }
    }

    /**
     * Reads payload data into provided buffer at specified offset.
     *
     * @param is
     * @param data
     * @param off
     * @return
     * @throws IOException
     */
    public int readPayload(byte[] data, int off, ByteBuffer... is) throws IOException {
        //System.out.println("[" + Thread.currentThread().getName() + "]readPayload(available=" + PDTools.getRemaining(is) + ", need=" + (data.length - off) + ", off=" + off);
        int len = (int) getLength();
        if (data != null && data.length - off < len) {
            throw new IOException("Insufficient buffer size for payload: got " + (data.length - off) + ", need " + len);
        }
        int c = (int) BufferTools.readTo(is, data, off, len);
        if (c != len) {
            throw new IOException("Failed to read required " + len + " payload bytes, got " + c);
        }
        // unmask if needed
        byte[] m = getMask();
        if (m != null) {
            for (int i = 0; i < len; i++) {
                data[i + off] ^= m[i % 4];
            }
        }
        return c;
    }

    /**
     * Writes payload from data buffer at specified offset.
     *
     * @param os
     * @param data
     * @param off
     * @param preserveData if true and masked, the data is masked before write
     * and unmasked after written.
     *
     * @throws IOException
     */
    private ByteBuffer writePayload(byte[] data, int off, boolean preserveData, ByteBuffer buf) throws IOException {
        int len = (int) getLength();
        if (data != null && data.length - off < len) {
            throw new IOException("Insufficient buffer size of payload: got " + (data.length - off) + ", need " + len);
        }
        // mask data if needed
        byte[] m = getMask();
        if (m != null) {
            for (int i = 0; i < len; i++) {
                data[i + off] ^= m[i % 4];
            }
        }
        try {
            if (buf == null) {
                byte[] tmp = Arrays.copyOfRange(data, off, off + len);
                return ByteBuffer.wrap(tmp);//os.write(data, off, len);
            } else {
                buf.put(data, off, off + len);
                return buf;
            }
        } finally {
            if (m != null && preserveData) {
                for (int i = 0; i < len; i++) {
                    data[i + off] ^= m[i % 4];
                }
            }
        }
    }

    public Object[] getExtensionData(WebSocketExtension ext) {
        if (extensionData != null) {
            return extensionData.get(ext);
        } else {
            return null;
        }
    }

    public void registerExtensionData(WebSocketExtension ext, Object... data) {
        if (ext == null) {
            return;
        }
        if (extensionData == null) {
            extensionData = new LinkedHashMap<Object, Object[]>();
        }
        extensionData.put(ext, data);
    }

    public byte getOpCode() {
        return frame.getByteHalf(0, true);
    }

    public void setOpCode(byte opcode) {
        frame.setByteHalf(0, true, opcode);
    }

    public boolean isFinalFragment() {
        return frame.getBit(0, (byte) 7);
    }

    public void setFinalFragment(boolean finalFragment) {
        frame.setBit(0, (byte) 7, finalFragment);
    }

    public boolean isReserved1() {
        return frame.getBit(0, (byte) 6);
    }

    public void setReserved1(boolean bit) {
        frame.setBit(0, (byte) 6, bit);
    }

    public boolean isReserved2() {
        return frame.getBit(0, (byte) 5);
    }

    public void setReserved2(boolean bit) {
        frame.setBit(0, (byte) 5, bit);
    }

    public boolean isReserved3() {
        return frame.getBit(0, (byte) 4);
    }

    public void setReserved3(boolean bit) {
        frame.setBit(0, (byte) 4, bit);
    }

    public boolean hasMask() {
        return frame.getBit(1, (byte) 7);
    }

    public byte[] getMask() {
        return (hasMask() && mask != null) ? Arrays.copyOf(mask, mask.length) : null;
    }

    public void setMask(byte[] mask) {
        if (mask != null) {
            frame.setBit(1, (byte) 7, true);
            this.mask = (mask.length == 4) ? mask : Arrays.copyOf(mask, 4);
        } else {
            frame.setBit(1, (byte) 7, false);
            mask = null;
        }
    }

    public void setRandomMask() {
        byte[] buf = new byte[4];
        ByteArray ba = new ByteArray(buf);
        ba.setInt(0, (int) (Math.random() * (Integer.MAX_VALUE - 1)) * ((Math.random() > 0.5) ? 1 : -1));
        setMask(buf);
    }

    public long getLength() {
        return len;
    }

    public void setLength(long len) {
        this.len = len;
        if (len < 126) {
        } else if (len <= 0xFFFF) {
            len = 126;
        } else {
            len = 127;
        }
        boolean masked = frame.getBit(1, (byte) 7);
        frame.setByte(1, (byte) (0x7F & len));
        frame.setBit(1, (byte) 7, masked);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WebSocketFrame{" + "fin=" + isFinalFragment() + ", r123=" + ((isReserved1()) ? 1 : 0) + ((isReserved2()) ? 1 : 0) + ((isReserved3()) ? 1 : 0) + ", op=" + getOpCode() + "/" + op_names.get(getOpCode()) + ", m=" + hasMask() + ", len=" + getLength());
        if (extensionData != null) {
            sb.append(", exts=");
            for (Object key : extensionData.keySet()) {
                sb.append("\n  ");
                sb.append(key.getClass().getSimpleName());
                sb.append(": ");
                sb.append(BufferTools.dump(extensionData.get(key)));
            }
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

}
