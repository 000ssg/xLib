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
package ssg.lib.http.base;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author 000ssg
 */
public class HttpResponse extends HttpData {

    HttpRequest request;

    public HttpResponse(HttpRequest request) {
        this.request = request;
        if (request != null) {
            setContext(request.getContext());
            if (!request.client) {
                setResponseCode(200, "OK");
                //onHeaderLoaded();
            }
        }
    }

    @Override
    public void onHeaderLoaded() {
        if (!getHead().completed) {
            getHead().completed = true;
        }
    }

    public boolean closed() {
        return request.closed;
    }

    public void close() throws IOException {
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        int idx = sb.indexOf("{") + 1;
        if (request != null && request.getHead() != null && request.getHead().getProtocolInfo() != null && request.getHead().getProtocolInfo().length > 1) {
            sb.insert(idx, "\n  request=" + request.getHead().getProtocolInfo()[0] + " " + request.getHead().getProtocolInfo()[1]);
        }
        return sb.toString();
    }

    public void setResponseCode(int code, String message) {
        getHead().setProtocol(httpVersion, code, message);
    }

    public int getResponseCode() {
        String[] ss = getHead().getProtocolInfo();
        if (ss != null && ss.length > 1) {
            try {
                return Integer.parseInt(ss[1]);
            } catch (Throwable th) {
            }
        }
        return -1;
    }

    public int getResponseMessage() {
        String[] ss = getHead().getProtocolInfo();
        if (ss != null && ss.length > 2) {
            try {
                return Integer.parseInt(ss[2]);
            } catch (Throwable th) {
            }
        }
        return -1;
    }

    public void setHeader(String hn, String hv) throws IOException {
        getHead().setHeader(hn, hv);
    }

    public void addHeader(String hn, String... hv) throws IOException {
        getHead().addHeader(hn, hv);
    }

    /**
     * Sets proper headers and pushes data if any.
     *
     * @param contentType
     * @param fileName
     * @param data
     * @param chunked
     * @throws IOException
     */
    public void prepareDownload(
            String contentType,
            String fileName,
            byte[] data,
            boolean chunked
    ) throws IOException {
        setHeader(HttpData.HH_CONTENT_TYPE, (contentType != null) ? contentType : "application/binary");
        if (fileName != null) {
            setHeader(HttpData.HH_CONTENT_DISPOSITION, "attachment; filename=" + fileName);
        }
        if (data != null && !chunked) {
            setHeader(HttpData.HH_CONTENT_LENGTH, "" + data.length);
        } else if (data == null || chunked) {
            setHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
        }
        onHeaderLoaded();
        if (data != null) {
            add(ByteBuffer.wrap(data));
        }
        onLoaded();
    }
}
