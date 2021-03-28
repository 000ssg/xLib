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

import ssg.lib.http.base.Head;
import static ssg.lib.websocket.WebSocketConstants.HH_CONNECTION;
import static ssg.lib.websocket.WebSocketConstants.HH_UPGRADE;
import static ssg.lib.websocket.WebSocketConstants.S_WS_E;
import static ssg.lib.websocket.WebSocketConstants.S_WS_K;
import static ssg.lib.websocket.WebSocketConstants.S_WS_P;
import static ssg.lib.websocket.WebSocketConstants.S_WS_S;
import static ssg.lib.websocket.WebSocketConstants.S_WS_V;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class WebSocketHandshake implements WebSocketConstants {

    public static final int WEBSOCKET_VERSION = 13;

    WebSocket ws;
    private boolean initialized = false;
    String key;

    // temporary handshaking data...
    String rkey; // handshaking key
    Head head = new Head();
    List<ByteBuffer> output;
    private boolean client = true;

    /**
     * Server-side websocket initialization: input contains client handshake
     *
     * @param input
     */
    public WebSocketHandshake(WebSocket ws) {
        this.ws = ws;
    }

    /**
     * Head-driven websocket handshake.
     *
     * @param ws
     * @param head
     * @throws IOException
     */
    public WebSocketHandshake(WebSocket ws, Head head) throws IOException {
        this.ws = ws;
        this.head = head;
        if (head != null && head.isHeadCompleted()) {
            if (head.isRequest()) {
                handleHandshakeRequest();
            } else if (head.isResponse()) {
                handleHandshakeResponse();
            }
        }
    }

    public WebSocketHandshake(
            WebSocket ws,
            String version,
            String path,
            String host,
            String origin,
            String[] proposedProtocols,
            String[] proposedExtensions,
            Integer wsVersion,
            Map<String, String> httpHeaders
    ) throws IOException {
        this.ws = ws;
        output = handshakeWriteClientRequest(version, path, host, origin, proposedProtocols, proposedExtensions, wsVersion, httpHeaders);
    }

    public long add(Collection<ByteBuffer> input) throws IOException {
        //System.out.println("[" + Thread.currentThread().getName() + "]WebSocketHandshake.add: " + PDTools.toText(null, input).replace("\n", "\n"));
        if (isInitialized()) {
            return 0;
        }
        long c = BufferTools.getRemaining(input);
        if (!head.isHeadCompleted() && input != null) {
            for (ByteBuffer bb : input) {
                head.add(bb);
                if (head.isHeadCompleted()) {
                    break;
                }
            }
        }

        if (head.isHeadCompleted()) {
            if (head.isRequest()) {
                // server mode -> analyse client request and generate response
                handleHandshakeRequest();
            } else if (head.isResponse()) {
                // client mode -> server response/acceptance expected: 101...
                handleHandshakeResponse();
            }
        }

        return c - BufferTools.getRemaining(input);
    }

    public List<ByteBuffer> get() throws IOException {
        try {
            return output;
        } finally {
            //System.out.println("[" + Thread.currentThread().getName() + "]WebSocketHandshake.get: " + PDTools.toText(null, output).replace("\n", "\n"));
            if (isInitialized()) {
                ws.setInitialized(true);
            }
            output = null;
        }
    }

    public List<ByteBuffer> handshakeWriteClientRequest(
            String version,
            String path,
            String host,
            String origin,
            String[] proposedProtocols,
            String[] proposedExtensions,
            Integer wsVersion,
            Map<String, String> httpHeaders
    ) throws IOException {
        // prepare key and response key
        key = java.util.Base64.getEncoder().encodeToString(("class " + getClass().getName().hashCode() + "_" + version).getBytes("UTF-8"));
        rkey = buildRKey(key);

        // generate request and send it
        StringBuilder req = new StringBuilder();
        req.append("GET " + path + " HTTP/1.1\r\n");
        //req.append(HH_CONNECTION + ": Upgrade\r\n");
        //req.append(HH_UPGRADE + ": websocket\r\n");
        req.append("Connection: Upgrade\r\n");
        req.append("Upgrade: websocket\r\n");
        req.append("Cache-Control: no-cache\r\n");
        req.append("Pragma: no-cache\r\n");
        if (host != null) {
            req.append("Host: " + host + "\r\n");
        }
        if (origin != null) {
            req.append("Origin: " + origin + "\r\n");
        }
        req.append(S_WS_K);
        req.append(": ");
        req.append(key);
        req.append("\r\n");
        if (proposedProtocols != null && proposedProtocols.length > 0) {
            req.append(S_WS_P);
            req.append(": ");
            for (int i = 0; i < proposedProtocols.length; i++) {
                if (i > 0) {
                    req.append(", ");
                }
                req.append(proposedProtocols[i]);
            }
            req.append("\r\n");
        }
        if (wsVersion != null) {
            req.append(S_WS_V);
            req.append(": " + ((wsVersion != 0) ? wsVersion : WEBSOCKET_VERSION));
            req.append("\r\n");
        }
        if (proposedExtensions != null) {
            for (String pe : proposedExtensions) {
                if (pe != null) {
                    pe = pe.trim();
                    if (!pe.isEmpty()) {
                        req.append(S_WS_E);
                        req.append(": " + pe);
                        req.append("\r\n");
                    }
                }
            }
        }

        // add other headers, e.g. session cookie or authorization details
        if (httpHeaders != null && !httpHeaders.isEmpty()) {
            for (Entry<String, String> e : httpHeaders.entrySet()) {
                req.append(e.getKey());
                req.append(":");
                req.append(e.getValue());
                req.append("\r\n");
            }
        }

        req.append("\r\n");
        return Collections.singletonList(ByteBuffer.wrap(req.toString().getBytes("UTF-8")));
    }

    String buildRKey(String key) throws IOException {
        try {
            return java.util.Base64.getEncoder().encodeToString(
                    MessageDigest
                            .getInstance("SHA-1")
                            .digest((key + S_WS_S)
                                    .getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException nsaex) {
            throw new IOException("Digest algorithm is not supported: " + nsaex, nsaex);
        }
    }

    public List<ByteBuffer> handshakeWriteServerResponse(
            String handshakeKey,
            String[] requestedProtocols,
            String[] requestedExtensions
    ) throws IOException {
        StringBuilder res = new StringBuilder();
        res.append("HTTP/1.1 101 Switching Protocols\r\n");
        res.append(HH_CONNECTION + ": Upgrade\r\n");
        res.append(HH_UPGRADE + ": websocket\r\n");
        res.append(S_WS_A);
        res.append(": ");
        try {
            res.append(buildRKey(handshakeKey));
//            res.append(java.util.Base64.getEncoder().encodeToString(
//                    MessageDigest
//                            .getInstance("SHA-1")
//                            .digest((handshakeKey + S_WS_S)
//                                    .getBytes("UTF-8")))
//            );
            res.append("\r\n");
        } catch (IOException ioex) {
//        } catch (NoSuchAlgorithmException nsaex) {
//            throw new IOException("Digest algorithm is not supported: " + nsaex, nsaex);
        }

        if (requestedProtocols != null) {
            for (String rp : requestedProtocols) {
                if (rp == null) {
                    continue;
                }
                rp = rp.trim();
                if (ws.acceptProtocol(rp)) {
                    res.append(S_WS_P);
                    res.append(": ");
                    res.append(rp);
                    res.append("\r\n");
                    break;
                }
            }
        }

        if (requestedExtensions != null) {
            String x = "";
            for (String rp : requestedExtensions) {
                rp = ws.acceptExtension(rp);
                if (rp != null) {
                    if (x.length() > 0) {
                        x += ",";
                    }
                    x += (rp);
                }
            }
            if (x.length() > 0) {
                res.append(S_WS_E);
                res.append(": ");
                res.append(x);
                res.append("\r\n");
            }
        }
        res.append("\r\n");

        return Collections.singletonList(ByteBuffer.wrap(res.toString().getBytes("UTF-8")));
    }

    /**
     * Server-side handshake request handler: analyze request and generate
     * acceptance response...
     *
     * @throws IOException
     */
    public void handleHandshakeRequest() throws IOException {
        // server mode -> analyse client request and generate response
        String[] handshakeKey = head.getHeader(S_WS_K.toUpperCase());
        String[] conn = head.getHeader(HH_CONNECTION);
        String[] upgr = head.getHeader(HH_UPGRADE);
        String[] prots = head.getHeader(S_WS_P.toUpperCase());
        String[] exts = head.getHeader(S_WS_E.toUpperCase());
        if (prots != null && prots.length > 0) {
            prots = prots[0].split(",");
        }

        if (handshakeKey != null) {
            output = this.handshakeWriteServerResponse(handshakeKey[0], prots, exts);
            setInitialized(true);
            //ws.setInitialized(true);
            client = false;
        } else {
            throw new IOException("Invalid WebSocket handshake: handshake key missing.");
        }
    }

    public void handleHandshakeResponse() throws IOException {
        // client mode -> server response/acceptance expected: 101...
        String[] rh = head.getProtocolInfo();
        if (rh != null && rh.length > 1 && "101".equals(rh[1])) {
            // check Connection
            String[] conn = head.getHeader(HH_CONNECTION);
            if (conn == null || conn.length != 1 || !conn[0].equalsIgnoreCase("upgrade")) {
                throw new IOException("WebSocket handshake failed: invalid or missing Connection header.");
            }

            // check Upgrade
            String[] upgr = head.getHeader(HH_UPGRADE);
            if (upgr == null || upgr.length != 1 || !upgr[0].equalsIgnoreCase(ws_protocol)) {
                throw new IOException("WebSocket handshake failed: invalid or missing Upgrade header.");
            }

            // check accept code
            String[] srkey = head.getHeader(S_WS_A.toUpperCase());
            if (srkey == null || srkey.length != 1 || !srkey[0].equals(rkey)) {
                throw new IOException("WebSocket handshake failed: invalid or missing accept code.");
            }

            // TODO: check protocol (if any)
            String[] prots = head.getHeader(S_WS_P.toUpperCase());
            if (prots != null) {
                for (String rp : prots) {
                    for (String rpi : rp.split(",")) {
                        rpi = rpi.trim();
                        if (ws.acceptProtocol(rpi.trim())) {
                            break;
                        }
                    }
                    if (ws.getProtocol() != null) {
                        break;
                    }
                }
            }
            // check extensions (if any)
            String[] exts = head.getHeader(S_WS_E.toUpperCase());
            if (exts != null) {
                for (String rp : exts) {
                    for (String rpi : rp.split(",")) {
                        rpi = rpi.trim();
                        String ok = ws.acceptExtension(rpi);
                        if (rpi == null || !rpi.equals(ok)) {
                            throw new IOException("WebSocket handshake failed: invalid or misconfigured extension: " + rpi + " -> " + ok);
                        }
                    }
                }
            }
            setInitialized(true);
            client = true;
            ws.setInitialized(true);
        } else {
            throw new IOException("WebSocket handshake failed: unexpected or non 101 response code.");
        }
    }

    /**
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * @param initialized the initialized to set
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * @return the client
     */
    public boolean isClient() {
        return client;
    }

}
