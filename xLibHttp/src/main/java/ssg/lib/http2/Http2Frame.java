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
package ssg.lib.http2;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author 000ssg
 */
public class Http2Frame {

    /*
    +---------------+------+--------------+
    | Frame Type    | Code | Section      |
    +---------------+------+--------------+
    | DATA          | 0x0  | Section 6.1  |
    | HEADERS       | 0x1  | Section 6.2  |
    | PRIORITY      | 0x2  | Section 6.3  |
    | RST_STREAM    | 0x3  | Section 6.4  |
    | SETTINGS      | 0x4  | Section 6.5  |
    | PUSH_PROMISE  | 0x5  | Section 6.6  |
    | PING          | 0x6  | Section 6.7  |
    | GOAWAY        | 0x7  | Section 6.8  |
    | WINDOW_UPDATE | 0x8  | Section 6.9  |
    | CONTINUATION  | 0x9  | Section 6.10 |
    +---------------+------+--------------+
     */
    public static final byte FT_DATA = 0x00;
    public static final byte FT_HEADERS = 0x01;
    public static final byte FT_PRIORITY = 0x02;
    public static final byte FT_RST_STREAM = 0x03;
    public static final byte FT_SETTINGS = 0x04;
    public static final byte FT_PUSH_PROMISE = 0x05;
    public static final byte FT_PING = 0x06;
    public static final byte FT_GOAWAY = 0x07;
    public static final byte FT_WINDOW_UPDATE = 0x08;
    public static final byte FT_CONTINUATION = 0x09;
    // frame flags
    public static byte FF_END_STREAM = 0x1;
    public static byte FF_END_HEADERS = 0x4;
    public static byte FF_PADDED = 0x8;
    public static byte FF_PRIORITY = 0x20;
    public static byte FF_ACK = 0x1;
    byte[] header = new byte[9];
    ByteBuffer data;
    int pos = 0; // data pos: 0-8 - header, 9- - data

    public Http2Frame() {
        header = new byte[9];
        data = null;
        pos = 0;
    }

    Http2Frame(Http2Frame base) {
        this.header = base.header;
        this.data = base.data;
        this.pos = base.pos;
    }

    public static <T extends Http2Frame> T toSpecificFrame(Http2Frame base) throws IOException {
        if (base == null || base instanceof FrameBase) {
            return (T) base;
        }
        switch (base.getType()) {
            case FT_DATA:
                return (T) new FrameData(base);
            case FT_HEADERS:
                return (T) new FrameHeaders(base);
            case FT_PRIORITY:
                return (T) new FramePriority(base);
            case FT_RST_STREAM:
                return (T) new FrameRstStream(base);
            case FT_SETTINGS:
                return (T) new FrameSettings(base);
            case FT_PUSH_PROMISE:
                return (T) new FramePushPromise(base);
            case FT_PING:
                return (T) new FramePing(base);
            case FT_GOAWAY:
                return (T) new FrameGoAway(base);
            case FT_WINDOW_UPDATE:
                return (T) new FrameWindowUpdate(base);
            case FT_CONTINUATION:
                return (T) new FrameContinuation(base);
            default:
                return null;
        }
    }

    public <T extends Http2Frame> T toSpecificFrame() throws IOException {
        return toSpecificFrame(this);
    }

    public void add(ByteBuffer... bbs) throws IOException {
        if (pos == -1) {
            throw new IOException("Cannot add data to completed Frame");
        }
        if (bbs != null) {
            for (ByteBuffer bb : bbs) {
                if (pos < 9) {
                    while (bb.hasRemaining() && pos < 9) {
                        header[pos++] = bb.get();
                    }
                    if (pos > 8) {
                        data = ByteBuffer.allocate(getLength());
                        // verify stream-type correlation
                        if (getStreamId() == 0) {
                            switch (getType()) {
                                case FT_DATA:
                                case FT_HEADERS:
                                case FT_PRIORITY:
                                case FT_RST_STREAM:
                                case FT_PUSH_PROMISE:
                                    // TODO: PROTOCOL_ERROR
                                    throw new IOException("The frame of type 0x" + Integer.toHexString(0xFF & getType()) + " must be associated with valid stream.");
                                case FT_SETTINGS:
                                case FT_PING:
                                case FT_GOAWAY:
                                case FT_WINDOW_UPDATE:
                                case FT_CONTINUATION:
                                default:
                            }
                        } else {
                            switch (getType()) {
                                case FT_DATA:
                                case FT_HEADERS:
                                case FT_PRIORITY:
                                case FT_RST_STREAM:
                                    break;
                                case FT_SETTINGS:
                                    // TODO: PROTOCOL_ERROR
                                    throw new IOException("The frame of type 0x" + Integer.toHexString(0xFF & getType()) + " cannot be associated with a stream.");
                                case FT_PUSH_PROMISE:
                                case FT_PING:
                                case FT_GOAWAY:
                                case FT_WINDOW_UPDATE:
                                case FT_CONTINUATION:
                                default:
                            }
                        }
                        if (getType() == FT_PRIORITY && getLength() != 5) {
                            // TODO: FRAME_SIZE_ERROR
                            throw new IOException("The frame of type 0x" + Integer.toHexString(0xFF & getType()) + " size mismatch: need 5, got " + getLength() + ".");
                        }
                    }
                    while (bb.hasRemaining() && data.hasRemaining()) {
                        data.put(bb.get());
                    }
                } else {
                    while (bb.hasRemaining() && data.hasRemaining()) {
                        data.put(bb.get());
                    }
                }
            }
        }
        // mark completed frame...
        if (data != null && !data.hasRemaining()) {
            pos = -1;
        }
    }

    public boolean isCompleted() {
        return pos == -1;
    }

    /**
     * Returns frame size or -1 if not ready yet (loaded less than 3 bytes of
     * header
     *
     * @return
     */
    public int getLength() {
        return (pos > 3 || pos == -1) ? (0xFF & header[0]) + (0xFF & header[1]) << 8 + (0xFF & header[2]) << 16 : -1;
    }

    /**
     * Returns frame size or -1 (0xFF) if not available (yet)
     *
     * @return
     */
    public byte getType() {
        return (pos > 4 || pos == -1) ? header[3] : -1;
    }

    public void setType(byte type) {
        header[3] = type;
    }

    /**
     * Returns frame flags or 0 (0x00) if not available (yet)
     *
     * @return
     */
    public byte getFlags() {
        return (pos > 5 || pos == -1) ? header[4] : 0;
    }

    public void setFlags(byte flags) {
        header[4] = flags;
    }

    /**
     * /**
     * Returns frame's stream id or -1 (0xFFFFFFFF) if not available (yet)
     *
     * @return
     */
    public int getStreamId() {
        return (pos > 9 || pos == -1) ? (0xFF & header[5]) + (0xFF & header[6]) << 8 + (0xFF & header[7]) << 16 + (0x7F & header[8]) << 24 : -1;
    }

    public static abstract class FrameBase extends Http2Frame {

        public FrameBase(Http2Frame base) throws IOException {
            super(base);
        }

        public FrameBase(int type, int flags) {
            setType((byte) (0xFF & type));
            setFlags((byte) (0xFF & flags));
        }

        public void setStreamId(int id) {
            header[5] = (byte) (0xFF & id);
            header[6] = (byte) (0xFF & (id >> 8));
            header[7] = (byte) (0xFF & (id >> 16));
            header[8] = (byte) (0x7F & (id >> 24));
        }

        public void setLength(int len) {
            header[0] = (byte) (0xFF & len);
            header[1] = (byte) (0xFF & (len >> 8));
            header[2] = (byte) (0xFF & (len >> 16));
        }
    }

    public static abstract class FrameBasePadded extends FrameBase {

        int padLength;

        public FrameBasePadded(Http2Frame base) throws IOException {
            super(base);
            padLength = (isPadded()) ? 0xFF & data.get() : 0;
            if (padLength > 0) {
                // TODO: adjust data to exclude padding...
                if (padLength >= getLength()) {
                    // TODO: PROTOCOL_ERROR
                    throw new IOException("Padding size is equal to or exceed data length: " + padLength + " >= " + getLength());
                }
                data.limit(data.limit() - padLength - 1);
            }
        }

        public FrameBasePadded(int type, int flags) {
            super(type, flags);
        }

        public int getPadLength() {
            return padLength;
        }

        public boolean isPadded() {
            return (getFlags() & FF_PADDED) == FF_PADDED;
        }
    }

    //   | DATA          | 0x0  | Section 6.1  |
    public static class FrameData extends FrameBasePadded {

        public FrameData(Http2Frame base) throws IOException {
            super(base);
        }

        public FrameData(int flags) {
            super(FT_DATA, flags & (FF_END_STREAM | FF_PADDED));
        }

        public boolean isEndStream() {
            return (getFlags() & FF_END_STREAM) == FF_END_STREAM;
        }
    }

    //   | HEADERS       | 0x1  | Section 6.2  |
    public static class FrameHeaders extends FrameBasePadded {

        Integer streamDependency;
        Integer weight;

        public FrameHeaders(Http2Frame base) throws IOException {
            super(base);
            if (isPriority()) {
                byte[] bb = new byte[5];
                data.get(bb);
                streamDependency = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0x7F & bb[3]) << 24;
                weight = 0xFF & bb[4];
            }
        }

        public FrameHeaders(int flags) {
            super(FT_DATA, flags & (FF_END_STREAM | FF_PADDED | FF_END_HEADERS | FF_PRIORITY));
        }

        public boolean isEndStream() {
            return (getFlags() & FF_END_STREAM) == FF_END_STREAM;
        }

        public boolean isEndHeaders() {
            return (getFlags() & FF_END_HEADERS) == FF_END_HEADERS;
        }

        public boolean isPriority() {
            return (getFlags() & FF_PRIORITY) == FF_PRIORITY;
        }

        public Integer getStreamDependency() {
            return streamDependency;
        }

        public Integer getWeight() {
            return weight;
        }
    }

    //   | PRIORITY      | 0x2  | Section 6.3  |
    public static class FramePriority extends FrameBase {

        Integer streamDependency;
        Integer weight;

        public FramePriority(Http2Frame base) throws IOException {
            super(base);
            if (isPriority()) {
                byte[] bb = new byte[5];
                data.get(bb);
                streamDependency = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0x7F & bb[3]) << 24;
                weight = 0xFF & bb[4];
            }
        }

        public boolean isPriority() {
            return (getFlags() & FF_PRIORITY) == FF_PRIORITY;
        }

        public Integer getStreamDependency() {
            return streamDependency;
        }

        public Integer getWeight() {
            return weight;
        }
    }

    //   | RST_STREAM    | 0x3  | Section 6.4  |
    public static class FrameRstStream extends FrameBase {

        long errorCode;

        public FrameRstStream(Http2Frame base) throws IOException {
            super(base);
            byte[] bb = new byte[4];
            data.get(bb);
            errorCode = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0xFF & bb[3]) << 24;
        }

        public long getErrorCode() {
            return errorCode;
        }
    }

    //   | SETTINGS      | 0x4  | Section 6.5  |
    public static class FrameSettings extends FrameBase {

        int[][] settings;

        public FrameSettings(Http2Frame base) throws IOException {
            super(base);
            if (isAck()) {
                if (getLength() > 0) {
                    // TODO: FRAME_SIZE_ERROR
                    throw new IOException("Invalid acknowlege settigs frame size: miust be 0, got " + getLength() + ".");
                }
            } else {
                int len = getLength();
                if (len == 0 || (len % 6) != 0) {
                    // TODO: FRAME_SIZE_ERROR
                    throw new IOException("Invalid acknowlege settigs frame size: miust be 0, got " + getLength() + ".");
                }
                settings = new int[getLength() / 6][];
                byte[] bb = new byte[6];
                for (int i = 0; i < settings.length; i++) {
                    data.get(bb);
                    int sID = (0xFF & bb[0]) | (0xFF & bb[1]) << 8;
                    int sValue = (0xFF & bb[2]) | (0xFF & bb[3]) << 8 | (0xFF & bb[4]) << 16 | (0xFF & bb[5]) << 24;
                    settings[i] = new int[]{sID, sValue};
                }
            }
        }

        public boolean isAck() {
            return (getFlags() & FF_ACK) == FF_ACK;
        }

        public int[][] getSettings() {
            return settings;
        }
    }

    //   | PUSH_PROMISE  | 0x5  | Section 6.6  |
    public static class FramePushPromise extends FrameBasePadded {

        int promisedStreamId;

        public FramePushPromise(Http2Frame base) throws IOException {
            super(base);
            byte[] bb = new byte[4];
            data.get(bb);
            promisedStreamId = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0x7F & bb[3]) << 24;
        }

        public int getPromisedStreamId() {
            return promisedStreamId;
        }

        public int getHeaderDataLength() {
            return getLength() - this.getPadLength() - 4;
        }

        public boolean isEndHeaders() {
            return (getFlags() & FF_END_HEADERS) == FF_END_HEADERS;
        }
    }

    //   | PING          | 0x6  | Section 6.7  |
    public static class FramePing extends FrameBase {

        byte[] opaque = new byte[8];

        public FramePing(Http2Frame base) throws IOException {
            super(base);
            data.get(opaque);
        }

        public boolean isAck() {
            return (getFlags() & FF_ACK) == FF_ACK;
        }

        public byte[] getOpaque() {
            return opaque;
        }
    }

    //   | GOAWAY        | 0x7  | Section 6.8  |
    public static class FrameGoAway extends FrameBase {

        int lastStreamId;
        int errorCode;

        public FrameGoAway(Http2Frame base) throws IOException {
            super(base);
            byte[] bb = new byte[4];
            data.get(bb);
            lastStreamId = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0x7F & bb[3]) << 24;
            data.get(bb);
            errorCode = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0xFF & bb[3]) << 24;
        }

        public int getDebugDataLen() {
            return getLength() - 4 - 4;
        }
    }

    //   | WINDOW_UPDATE | 0x8  | Section 6.9  |
    public static class FrameWindowUpdate extends FrameBase {

        int windowSizeIncrement;

        public FrameWindowUpdate(Http2Frame base) throws IOException {
            super(base);
            byte[] bb = new byte[4];
            data.get(bb);
            windowSizeIncrement = (0xFF & bb[0]) | (0xFF & bb[1]) << 8 | (0xFF & bb[2]) << 16 | (0x7F & bb[3]) << 24;
        }
    }

    //   | CONTINUATION  | 0x9  | Section 6.10 |
    public static class FrameContinuation extends FrameBase {

        public FrameContinuation(Http2Frame base) throws IOException {
            super(base);
        }

        public boolean isEndHeaders() {
            return (getFlags() & FF_END_HEADERS) == FF_END_HEADERS;
        }
    }

}
