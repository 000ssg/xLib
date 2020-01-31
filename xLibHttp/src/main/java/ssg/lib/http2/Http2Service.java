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

import ssg.lib.http.base.HttpRequest;
import static ssg.lib.http2.Http2Frame.FT_CONTINUATION;
import static ssg.lib.http2.Http2Frame.FT_DATA;
import static ssg.lib.http2.Http2Frame.FT_GOAWAY;
import static ssg.lib.http2.Http2Frame.FT_HEADERS;
import static ssg.lib.http2.Http2Frame.FT_PING;
import static ssg.lib.http2.Http2Frame.FT_PRIORITY;
import static ssg.lib.http2.Http2Frame.FT_PUSH_PROMISE;
import static ssg.lib.http2.Http2Frame.FT_RST_STREAM;
import static ssg.lib.http2.Http2Frame.FT_SETTINGS;
import static ssg.lib.http2.Http2Frame.FT_WINDOW_UPDATE;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class Http2Service {

    public static final String HH_UPGRADE = "Upgrade";
    public static final String HH_HTTP2_SETTINGS = "HTTP2-Settings";
    public static final String HH_PROTOCOL_H2 = "h2";
    public static final String HH_PROTOCOL_H2C = "h2c";

    public static final String H2_CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSN\r\n\r\n";

    public static enum STREAM_STATE {
        idle,
        reserved_local,
        reserved_remote,
        open,
        halfClosed_local,
        halfClosed_remote,
        closed
    }

//    @Override
//    public HttpRequest getRequest(ServiceDataManager sdm, ServiceData data, boolean create, boolean secure) throws IOException {
//        HttpRequest req = null;
//        String[] pp = HttpService.probeHttpRequestData(data.get());
//        if (pp != null && HttpService.httpMethodNames.contains(pp[0])) {
//            req = super.getRequest(sdm, data, create, secure);
//            String[] connection = req.getHead().getHeader(HttpData.HH_CONNECTION);
//            for (String s : connection) {
//                String h = req.getHead().getHeader1(s);
//                if (HH_UPGRADE.equalsIgnoreCase(s)) {
//                    String h2 = h;
//                    if (h2 != null && (h2.equals(HH_PROTOCOL_H2) || h2.equals(HH_PROTOCOL_H2C))) {
//                        // -> Http2 req/resp...
//                    }
//                } else if (HH_HTTP2_SETTINGS.equalsIgnoreCase(s)) {
//
//                }
//            }
//        }
//        return req;
//    }
    public static class Http2Multiplexer {

        /* settings...
   +------------------------+------+---------------+---------------+
   | Name                   | Code | Initial Value | Specification |
   +------------------------+------+---------------+---------------+
   | HEADER_TABLE_SIZE      | 0x1  | 4096          | Section 6.5.2 |
   | ENABLE_PUSH            | 0x2  | 1             | Section 6.5.2 |
   | MAX_CONCURRENT_STREAMS | 0x3  | (infinite)    | Section 6.5.2 |
   | INITIAL_WINDOW_SIZE    | 0x4  | 65535         | Section 6.5.2 |
   | MAX_FRAME_SIZE         | 0x5  | 16384         | Section 6.5.2 |
   | MAX_HEADER_LIST_SIZE   | 0x6  | (infinite)    | Section 6.5.2 |
   +------------------------+------+---------------+---------------+
         */
 /* error codes
   +---------------------+------+----------------------+---------------+
   | Name                | Code | Description          | Specification |
   +---------------------+------+----------------------+---------------+
   | NO_ERROR            | 0x0  | Graceful shutdown    | Section 7     |
   | PROTOCOL_ERROR      | 0x1  | Protocol error       | Section 7     |
   |                     |      | detected             |               |
   | INTERNAL_ERROR      | 0x2  | Implementation fault | Section 7     |
   | FLOW_CONTROL_ERROR  | 0x3  | Flow-control limits  | Section 7     |
   |                     |      | exceeded             |               |
   | SETTINGS_TIMEOUT    | 0x4  | Settings not         | Section 7     |
   |                     |      | acknowledged         |               |
   | STREAM_CLOSED       | 0x5  | Frame received for   | Section 7     |
   |                     |      | closed stream        |               |
   | FRAME_SIZE_ERROR    | 0x6  | Frame size incorrect | Section 7     |
   | REFUSED_STREAM      | 0x7  | Stream not processed | Section 7     |
   | CANCEL              | 0x8  | Stream cancelled     | Section 7     |
   | COMPRESSION_ERROR   | 0x9  | Compression state    | Section 7     |
   |                     |      | not updated          |               |
   | CONNECT_ERROR       | 0xa  | TCP connection error | Section 7     |
   |                     |      | for CONNECT method   |               |
   | ENHANCE_YOUR_CALM   | 0xb  | Processing capacity  | Section 7     |
   |                     |      | exceeded             |               |
   | INADEQUATE_SECURITY | 0xc  | Negotiated TLS       | Section 7     |
   |                     |      | parameters not       |               |
   |                     |      | acceptable           |               |
   | HTTP_1_1_REQUIRED   | 0xd  | Use HTTP/1.1 for the | Section 7     |
   |                     |      | request              |               |
   +---------------------+------+----------------------+---------------+
         */
        public int S_HEADER_TABLE_SIZE = 4096; // 0x1
        public int S_ENABLE_PUSH = 1; // 0x2
        public int S_MAX_CONCURRENT_STREAMS = Integer.MAX_VALUE; // 0x3
        public int S_INITIAL_WINDOW_SIZE = 65535; // 0x4
        public int S_MAX_FRAME_SIZE = 16384; // 0x5
        public int S_MAX_HEADER_LIST_SIZE = Integer.MAX_VALUE; // 0x6

        Http2Frame frame;
        Map<Integer, Http2Stream> streams = new LinkedHashMap<Integer, Http2Stream>();
        int window = S_INITIAL_WINDOW_SIZE;

        public long add(Collection<ByteBuffer>... bbs) throws IOException {
            long c = BufferTools.getRemaining(bbs);
            if (bbs != null && bbs.length > 0) {
                for (Collection<ByteBuffer> bs : bbs) {
                    if (bs != null && !bs.isEmpty()) {
                        for (ByteBuffer bb : bs) {
                            if (bb != null && bb.hasRemaining()) {
                                while (bb.hasRemaining()) {
                                    if (frame == null) {
                                        frame = new Http2Frame();
                                    }
                                    frame.add(bb);
                                    if (frame.isCompleted()) {
                                        processFrame(frame);
                                        frame = null;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return c - BufferTools.getRemaining(bbs);
        }

        public List<ByteBuffer> get() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void close() throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void processFrame(Http2Frame... frames) throws IOException {
            for (Http2Frame frame : frames) {
                int streamId = frame.getStreamId();
                Http2Stream stream = (streamId != 0) ? streams.get(streamId) : null;
                switch (frame.getType()) {
                    case FT_PUSH_PROMISE:
                    case FT_HEADERS:
                        if (stream == null) {
                            stream = new Http2Stream();
                            stream.id = streamId;
                        }
                    case FT_CONTINUATION:
                    case FT_DATA:
                    case FT_PRIORITY:
                    case FT_RST_STREAM:
                        // TODO: PROTOCOL_ERROR
                        if (stream == null) {
                            throw new IOException("No stream for frame: " + frame);
                        }
                        stream.add(frame.toSpecificFrame());
                        break;
                    case FT_SETTINGS:
                        Http2Frame.FrameSettings frameSettings = frame.toSpecificFrame();
                        if (!frameSettings.isAck()) {
                            for (int[] setting : frameSettings.getSettings()) {
                                switch (setting[0]) {
                                    case 1:
                                        S_HEADER_TABLE_SIZE = setting[1]; // 0x1
                                        break;
                                    case 2:
                                        S_ENABLE_PUSH = setting[1]; // 0x2
                                        break;
                                    case 3:
                                        S_MAX_CONCURRENT_STREAMS = setting[1]; // 0x3
                                        break;
                                    case 4:
                                        S_INITIAL_WINDOW_SIZE = setting[1]; // 0x4
                                        break;
                                    case 5:
                                        S_MAX_FRAME_SIZE = setting[1]; // 0x5
                                        break;
                                    case 6:
                                        S_MAX_HEADER_LIST_SIZE = setting[1]; // 0x6
                                        break;
                                    default:
                                        throw new IOException("Unrecognized setting: id=" + setting[0] + ", value=" + setting[1]);
                                }
                            }
                            // TODO: send ACK?
                        } else {
                            // TODO: settings acknowledged, are we happy?
                        }
                        break;
                    case FT_PING:
                        break;
                    case FT_GOAWAY:
                        break;
                    case FT_WINDOW_UPDATE:
                        break;
                    default:
                }
            }
        }

    }

    public static class Http2Request extends HttpRequest {
    }
}
