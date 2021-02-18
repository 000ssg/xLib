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

import ssg.lib.http.HttpMatcher;
import static ssg.lib.http.base.HttpData.HCONN_UPGRADE;
import static ssg.lib.http.base.HttpData.HH_CONNECTION;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import static ssg.lib.http.base.HttpData.HCONN_CLOSE;

/**
 * Represents Http headers section: 1st line for protocol (request/response) and
 * headers up to double CRLF.
 */
public class Head {

    HttpData http;

    String[] protocol;
    Map<String, String[]> headers = new LinkedHashMap<String, String[]>();
    // header name in ucase -> actually used header name!
    Map<String, String> headUCased = new HashMap<String, String>();
    boolean completed = false;
    boolean chunked = false;
    Long size;
    private boolean sent = false;
    private boolean connectionUpgrade = false;
    private boolean connectionClose = false;
    // identifier
    HttpMatcher matcher;
    // wip
    boolean iCR = false; // header CRLF delimiter - 1st char
    StringBuilder sb = new StringBuilder(); // text accumulated from previous delimiter

    public Head() {
    }

    public Head(HttpData http) {
        this.http = http;
    }

    public boolean isHeadCompleted() {
        return completed;
    }

    public Map<String, String[]> getHeaders() {
        return headers;
    }

    public void initFrom(Head head) throws IOException {
        protocol = (head.protocol != null) ? Arrays.copyOf(head.protocol, head.protocol.length) : null;
        completed = false;
        chunked = false;
        setSent(false);
        iCR = false;
        sb.delete(0, sb.length());
        for (String hn : head.headers.keySet()) {
            String[] hv = head.headers.get(hn);
            addHeader(hn, hv);
        }
    }

    /**
     * Http Request head
     *
     * @param method
     * @param uri
     * @param version
     */
    public void setProtocol(String method, String uri, String version) {
        protocol = new String[]{method, uri, version};
    }

    /**
     * Http response head
     *
     * @param version
     * @param code
     * @param message
     */
    public void setProtocol(String version, int code, String message) {
        protocol = new String[]{(version != null) ? version : http.httpVersion, "" + code, message};
        matcher = null;
        updateMatcher();
    }

    /**
     * Returns copy of "protocol" values if available.
     *
     * @return
     */
    public String[] getProtocolInfo() {
        return (protocol != null) ? Arrays.copyOf(protocol, protocol.length) : null;
    }

    public boolean isConnectionUpgrade() {
        return connectionUpgrade;
    }

    public boolean isConnectionClose() {
        // connection should be closed if explicitly set or if HTTP/1.0 && no "Connection" header.
        return connectionClose || isResponse() && protocol[0].endsWith("1.0") && this.getHeader(HH_CONNECTION) == null;
    }

    public boolean isRequest() {
        return protocol != null && !protocol[0].startsWith("HTTP");
    }

    public boolean isResponse() {
        return protocol != null && protocol[0].startsWith("HTTP");
    }

    public void updateMatcher() {
        if (matcher == null) {
            if (isRequest()) {//protocol != null && !protocol[0].startsWith("HTTP")) {
                if (protocol.length > 1) {
                    matcher = new HttpMatcher(protocol[1], protocol[0]);
                    String ct = getHeader1(HttpData.HH_CONTENT_TYPE);
                    if (ct != null) {
                        matcher.setContentType(ct);
                    }
                } else {
                    // TODO: invalid protocol (must be space-separated 2 or 3 values) -> ???
                    int a = 0;
                }
            }
        } else {
            if (matcher.getContentType() == null) {
                String ct = getHeader1(HttpData.HH_CONTENT_TYPE);
                if (ct != null) {
                    matcher.setContentType(ct);
                }
            }
        }
    }

    public void add(ByteBuffer bb) throws IOException {
        while (!completed && bb.hasRemaining()) {
            char ch = (char) bb.get();
            switch (ch) {
                case HttpData.CR:
                    if (!iCR) {
                        // new delimiter?
                        iCR = true;
                    } else {
                        //
                        sb.append(ch);
                    }
                    break;
                case HttpData.LF:
                    if (iCR) {
                        if (sb.length() == 0) {
                            // EOH
                            completed = true;
                            sb = null;
                            iCR = false;
                            updateMatcher();
                        } else {
                            if (protocol == null) {
                                protocol = sb.toString().split(" ");
                                matcher = null;
                                updateMatcher();
                            } else {
                                int idx = sb.indexOf(":");
                                String hn = sb.substring(0, idx).trim();
                                String hv = sb.substring(idx + 1).trim();
                                addHeader(hn, hv);
                            }
                            sb.delete(0, sb.length());
                            iCR = false;
                        }
                    } else {
                        sb.append(ch);
                    }
                    break;
                default:
                    if (ch >= ' ' && ch < 128) {
                        sb.append(ch);
                    } else {
                        throw new IOException("Unrecognized character in header: " + (char) ch + " / " + (int) ch);
                    }
            }
        }
    }

    public void setHeader(String hn, String hv) throws IOException {
        String hh = headUCased.get(hn.toUpperCase());
        if (hh == null) {
            if (hv == null) {
                return;
            }
            headUCased.put(hn.toUpperCase(), hn);
        } else {
            hn = hh;
        }
        if (hn != null && hv != null) {
            String[] ss = new String[]{hv};
            onHeaderSet(hn, ss);
            headers.put(hn, ss);
        } else if (hn != null && hv == null && headers.containsKey(hn)) {
            headers.remove(hn);
        }
    }

    public void addHeader(String hn, String... hvs) throws IOException {
        if (hvs == null || hvs.length == 0) {
            return;
        }
        {
            String hh = headUCased.get(hn.toUpperCase());
            if (hh != null) {
                hn = hh;
            } else {
                headUCased.put(hn.toUpperCase(), hn);
            }
        }
        String[] ss = headers.get(hn);
        if (ss == null) {
            hvs = Arrays.copyOf(hvs, hvs.length);
            onHeaderSet(hn, hvs);
            headers.put(hn, hvs);
        } else {
            int off = ss.length;
            ss = Arrays.copyOf(ss, ss.length + hvs.length);
            for (int i = 0; i < hvs.length; i++) {
                ss[off + i] = hvs[i];
            }
            onHeaderSet(hn, ss);
            headers.put(hn, ss);
        }
    }

    public void onHeaderSet(String hn, String... hvs) throws IOException {
        if (hn == null || hvs == null || hvs.length == 0) {
            return;
        }

        if (HH_CONNECTION.equalsIgnoreCase(hn)) {
            if (HCONN_UPGRADE.equalsIgnoreCase(hvs[0])) {
                connectionUpgrade = true;
            } else if (HCONN_CLOSE.equalsIgnoreCase(hvs[0])) {
                connectionClose = true;
            }
        }
        if ("TRANSFER-ENCODING".equalsIgnoreCase(hn) && "CHUNKED".equalsIgnoreCase(hvs[0])) {
            chunked = true;
        } else if ("CONTENT-LENGTH".equalsIgnoreCase(hn)) {
            try {
                size = Long.parseLong(hvs[0]);
            } catch (Throwable th) {
                throw new IOException("Unparseable Content-Length: " + th);
            }
        }
    }

    public String getHeader1(String hn) {
        String[] hv = getHeader(hn);
        if (hv != null && hv.length > 0) {
            return hv[0];
        }
        return null;
    }

    public String[] getHeader(String hn) {
        hn = (hn != null) ? headUCased.get(hn.toUpperCase()) : hn;
        if (hn == null || hn.isEmpty()) {
            return null;
        }
        for (String k : headers.keySet()) {
            if (hn.equalsIgnoreCase(k)) {
                return headers.get(k);
            }
        }
        return null;
    }

    public ByteBuffer getByteBuffer() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol[0]);
        sb.append(' ');
        sb.append(protocol[1]);
        sb.append(' ');
        sb.append(protocol[2]);
        sb.append(HttpData.CRLF);
        for (String hn : headers.keySet()) {
            String[] hvs = headers.get(hn);
            for (String hv : hvs) {
                sb.append(hn);
                sb.append(':');
                sb.append(hv);
                sb.append(HttpData.CRLF);
            }
        }
        sb.append(HttpData.CRLF);
        return ByteBuffer.wrap(sb.toString().getBytes());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("\n  protocol=");
        if (protocol != null) {
            for (String p : protocol) {
                sb.append(' ' + p);
            }
        } else {
            sb.append("<none>");
        }
        sb.append("\n  headers=");
        sb.append(headers.size());
        for (String hn : headers.keySet()) {
            String[] hvs = headers.get(hn);
            sb.append("\n    ");
            sb.append(hn);
            sb.append(":");
            if (hvs == null || hvs.length == 0) {
            } else if (hvs.length == 1) {
                sb.append(' ');
                sb.append(hvs[0]);
            } else {
                sb.append(hvs.length);
                sb.append(':');
                for (String hv : hvs) {
                    sb.append('|');
                    sb.append(hv);
                }
            }
        }
        sb.append("\n  completed=");
        sb.append(completed);
        sb.append("\n  chunked=");
        sb.append(chunked);
        sb.append("\n  size=");
        sb.append(size);
        sb.append("\n  iCR=");
        sb.append(iCR);
        sb.append("\n  sb=");
        sb.append(("" + this.sb).replace("\n", "\n  "));
        sb.append("\n");
        sb.append('}');
        return sb.toString();
    }

    /**
     * @return the sent
     */
    public boolean isSent() {
        return sent;
    }

    /**
     * @param sent the sent to set
     */
    public void setSent(boolean sent) {
        this.sent = sent;
    }

}
