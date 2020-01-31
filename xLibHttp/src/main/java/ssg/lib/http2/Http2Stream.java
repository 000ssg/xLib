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

/**
 *
 * @author 000ssg
 */
public class Http2Stream {

    // indicates stream is initiated by this end (if false - by the other end)
    boolean local = true;
    // stream state, initial -> idle
    Http2Service.STREAM_STATE state = Http2Service.STREAM_STATE.idle;
    // stream id
    int id;
    int window;
    HPack requestHPack = new HPack();
    HPack responseHPack = new HPack();

    public Http2Stream() {
    }

    public Http2Stream(int id) {
        this.id = id;
    }

    public Http2Stream(int id, int window) {
        this.id = id;
        this.window = window;
    }

    public void add(Http2Frame... frames) throws IOException {
        if (frames != null) {
            for (Http2Frame frame : frames) {
                switch (frame.getType()) {
                    case FT_PUSH_PROMISE:
                    case FT_HEADERS:
                        switch (state) {
                            case idle:
                            case reserved_local:
                                state = Http2Service.STREAM_STATE.open;
                            case open:
                            case halfClosed_remote:
                                requestHPack.add(frame.data);
                                break;
                            case reserved_remote:
                            case halfClosed_local:
                            case closed:
                                throw new IOException("Invalid stream state: cannot accept headers in state other than '" + Http2Service.STREAM_STATE.idle + "', current state: '" + state + "'.");
                        }
                        break;
                    case FT_CONTINUATION:
                        break;
                    case FT_DATA:
                        switch (state) {
                            case open:
                            case halfClosed_remote:
                                break;
                            case idle:
                            case halfClosed_local:
                            case reserved_local:
                            case reserved_remote:
                            case closed:
                            default:
                                // TODO: STREAM_CLOSED
                                throw new IOException("Invalid stream state: cannot accept data in states other than '" + Http2Service.STREAM_STATE.open + "' or '" + Http2Service.STREAM_STATE.halfClosed_local + "' or '" + Http2Service.STREAM_STATE.halfClosed_remote + "', current state: '" + state + "'.");
                        }
                        break;
                    case FT_PRIORITY:
                        break;
                    case FT_RST_STREAM:
                        switch (state) {
                            case open:
                            case halfClosed_remote:
                            case halfClosed_local:
                            case reserved_local:
                            case reserved_remote:
                            case closed:
                                // TODO: immediately close stream...
                                break;
                            case idle:
                            default:
                                // TODO: PROTOCOL_ERROR
                                throw new IOException("Invalid stream state: cannot immediately close already closed stream.");
                        }
                        break;
                    case FT_SETTINGS:
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

    public Http2Frame[] get() throws IOException {
        return null;
    }

}
