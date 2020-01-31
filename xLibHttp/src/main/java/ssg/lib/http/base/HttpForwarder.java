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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import ssg.lib.common.Replacement;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.buffers.ByteBufferPipeReplacement;

/**
 *
 * @author 000ssg
 */
public class HttpForwarder {

    Channel local;
    Channel remote;
    // data wrappers
    HttpRequest localReq;
    HttpRequest remoteReq;
    HttpResponse localResp;
    HttpResponse remoteResp;
    List<ByteBuffer> responseCache = new ArrayList<ByteBuffer>();
    // recoding data
    String remoteHost;
    String localHost;
    String remoteHostWithPort;
    String localHostWithPort;
    boolean localIsSecure = false;
    boolean remoteIsSecure = false;
    Replacement replacement;
    ByteBuffer replacerBuf = ByteBuffer.allocate(4096);

    public HttpForwarder(Channel local, Channel remote) {
        this.local = local;
        this.remote = remote;
        init();
    }

    public HttpForwarder(Channel local, boolean localIsSecure, Channel remote, boolean remoteIsSecure) {
        this.local = local;
        this.remote = remote;
        this.localIsSecure = localIsSecure;
        this.remoteIsSecure = remoteIsSecure;
        init();
    }

    void init() {
        evalHosts((NetworkChannel) local, (SocketChannel) remote);
    }

    public void evalHosts(NetworkChannel local, SocketChannel remote) {
        String remoteHost = null;
        String localHost = null;
        if (remoteHost == null && remote != null) {
            try {
                remoteHost = remote.getRemoteAddress().toString();
                int idx = remoteHost.indexOf("/");
                remoteHostWithPort = (idx > 1) ? remoteHost.substring(0, idx) + remoteHost.substring(remoteHost.indexOf(":") - 1) : remoteHost.substring(1);
                remoteHost = (idx > 1) ? remoteHost.substring(0, idx) : remoteHost.substring(1, remoteHost.indexOf(":"));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (localHost == null && local != null) {
            try {
                localHost = local.getLocalAddress().toString();
                int idx = localHost.indexOf("/");
                localHostWithPort = (idx > 1) ? localHost.substring(0, idx) + localHost.substring(localHost.indexOf(":") - 1) : localHost.substring(1);
                localHost = (idx > 1) ? localHost.substring(0, idx) : localHost.substring(1, localHost.indexOf(":"));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (localHost != null) {
            this.localHost = localHost;
        }
        if (remoteHost != null) {
            this.remoteHost = remoteHost;
        }
        if (this.localHost != null && this.remoteHost != null) {
            replacement = new Replacement(this.localHost, this.remoteHost);
        }
    }

    void close(boolean remote) {
        //
        reset();
    }

    public void reset() {
        localReq = null;
        remoteReq = null;
        localResp = null;
        remoteResp = null;
        ((Buffer) replacerBuf).clear();
        responseCache.clear();
    }

    public int getPreferredBufferSize(boolean internal) {
        return 4096;
    }

    /**
     * Accepts request for forwarding
     *
     * @param bbs
     * @throws IOException
     */
    public void writeInternal(Collection<ByteBuffer>... bbs) throws IOException {
        if (localReq != null && localResp != null) {
            // reset forwarding if previous req/resp is done...
            if (localResp.isCompleted()) {
                close(true);
            }
        }
        // request data
        if (localReq == null) {
            reset();
            localReq = new HttpRequest();
            localReq.setAllowMultipartBody(false);
            for (int i = 0; i < bbs.length; i++) {
                localReq.add(bbs[i]);
            }
        } else {
            localReq.add(bbs);
        }
        // prepare forwardable version of rewquest once local request head is recognized
        if (remoteReq == null && localReq.isHeaderLoaded()) {
            remoteReq = new HttpRequest(true);
            remoteReq.setAllowMultipartBody(false);
            remoteReq.getHead().initFrom(localReq.getHead());
            fixRemoteRequestHeader();
        }
        if (remoteReq != null && localReq != null) {
            if (localReq.isCompleted()) {
                // ensure any request data from local are copied to remote
                Collection<ByteBuffer> data = null;
                while ((data = localReq.getBody().removeAsCollection()) != null) {
                    if (data.isEmpty()) {
                        break;
                    }
                    for (ByteBuffer bb : data) {
                        remoteReq.getBody().add(bb);
                    }
                }
                remoteReq.onLoaded();
            }
        }
    }

    /**
     * Remote request data
     *
     * @return
     * @throws IOException
     */
    public ByteBuffer[] readExternal() throws IOException {
        ByteBuffer[] r = null;
        if (remoteReq != null && !remoteReq.isSent()) {
            if (!remoteReq.getHead().isSent()) {
                remoteReq.fixHeadersBeforeSend(false);
                r = new ByteBuffer[]{remoteReq.getHead().getByteBuffer()};
                remoteReq.getHead().setSent(true);
            }
            if (!remoteReq.isSent()) {
                List<ByteBuffer> rr = remoteReq.get();
                if (rr != null && !rr.isEmpty()) {
                    if (r != null) {
                        int off = r.length;
                        r = Arrays.copyOf(r, off + rr.size());
                        for (int i = 0; i < rr.size(); i++) {
                            r[off + i] = rr.get(i);
                        }
                    } else {
                        r = rr.toArray(new ByteBuffer[rr.size()]);
                    }
                }
            }
        }
        return r;
    }

    /**
     * Remote response data
     *
     * @param bbs
     * @throws IOException
     */
    public void writeExternal(Collection<ByteBuffer>... bbs) throws IOException {
        if (remoteResp == null && remoteReq != null) {
            remoteResp = new HttpResponse(remoteReq);
            remoteResp.setAllowMultipartBody(false);
        }
        if (remoteResp != null) {
            if (bbs != null) {
                remoteResp.add(bbs);
            }
            if (localResp == null && remoteResp.isHeaderLoaded()) {
                localResp = new HttpResponse(localReq);
                localResp.setAllowMultipartBody(false);
                localResp.getHead().initFrom(remoteResp.getHead());
                if (localResp.getContentLength() != null && localResp.getContentLength() > 0) {
                    // remove content length
                    localResp.setHeader("Content-Length", null);
                    localResp.getHead().size = null;
                    localResp.size = 0;
                    if (!localResp.isChunked()) {
                        localResp.setHeader("Transfer-Encoding", "chunked");
                        //localResp.getHead().chunked = true;
                    }
                }
                if (!localResp.isChunked() && (localResp.getContentLength() != null && localResp.getContentLength() > 0)) {
                    localResp.setHeader("Transfer-Encoding", "chunked");
                    //localResp.getHead().chunked = true;
                }
                if (needReplacement(localResp.getContentType())) {
                    localResp.input = new ByteBufferPipeReplacement(replacement.copy());
                }
                localResp.onHeaderLoaded();
            }
            if (localResp != null && !localResp.isCompleted()) {
                boolean rrc = remoteResp.isCompleted();
                remoteResp.getHead().setSent(true);
                ByteBuffer[] r = null;
                if (!remoteResp.getBody().data().isEmpty()) {
                    r = remoteResp.getBody().data().toArray(new ByteBuffer[remoteResp.getBody().data().size()]);
                    remoteResp.getBody().data().clear();
                }

                if (r != null) {
                    for (ByteBuffer bb : r) {
                        if (bb != null && bb.hasRemaining()) {
                            int bc = bb.remaining();
                            String sss = BufferTools.toText(null, bb);
                            long bbc = localResp.getBody().length;
                            localResp.add(bb);
                            //System.out.println("ADD " + bc + "->LRESP: " + bbc + " -> " + localResp.getBody().length+"\n  "+sss.replace("\n", "\n  "));
                        }
                    }
                    if (rrc) {
                        if (localResp.input != null && !localResp.input.isClosed()) {
                            localResp.input.close();
                            localResp.add(ByteBuffer.allocate(0));
                        }
                    }
                }
                if (rrc) {
                    localResp.onLoaded();
                }
            }
        }
    }

    /**
     * Local response data
     *
     * @param bb
     * @return
     * @throws IOException
     */
    public int readInternal(ByteBuffer bb) throws IOException {
        int r = 0;
        if (localResp != null) {
            if (localResp.isSent()) {
                r = -1;
            } else {
                if (!localResp.getHead().isSent()) {
                    fixLocalResponseHeader();
                    localResp.fixHeadersBeforeSend(false);
                    responseCache.add(localResp.getHead().getByteBuffer());
                    localResp.getHead().setSent(true);
                }
                if (!localResp.isSent()) {
                    List<ByteBuffer> bbs = localResp.get();
                    if (bbs != null && !bbs.isEmpty()) {
                        for (ByteBuffer rbb : bbs) {
                            responseCache.add(rbb);
                        }
                    }
                }
            }
        }
        if (!responseCache.isEmpty() && bb != null && bb.hasRemaining()) {
            Iterator<ByteBuffer> it = responseCache.iterator();
            while (it.hasNext()) {
                ByteBuffer rb = it.next();
                int c = rb.remaining();
                if (rb.hasRemaining()) {
                    if (rb.remaining() > bb.remaining()) {
                        while (bb.hasRemaining()) {
                            bb.put(rb.get());
                        }
                    } else {
                        bb.put(rb);
                    }
                    r += c - rb.remaining();
                }
                if (!rb.hasRemaining()) {
                    it.remove();
                }
            }
        }
        return r;
    }

    public boolean hasExternalData() {
        if (!responseCache.isEmpty()) {
            return true;
        } else if (localResp == null) {
            return false;
        } else if (localResp.isCompleted() && !localResp.getHead().isSent() || localResp.getBody().size() > 0) {
            return true;
        } else {
            return false;
        }
    }

    public boolean needReplacement(String contentType) {
        if (1 == 0) {
            return false;
        } else if (contentType == null) {
            return false;
        } else if (contentType.contains("image")) {
            return false;
        } else if (contentType.contains("multipart-")) {
            return false;
        } else {
            return contentType.contains("text") || contentType.contains("json") || contentType.contains("xml") || contentType.contains("script");
        }
    }

    /**
     * Performs corrections to remote request header: adjusts "host" value and
     * converts to chunked stream if has data.
     */
    public void fixRemoteRequestHeader() throws IOException {
        // adjust host...
        remoteReq.setHeader("host", remoteHost);
        Long cl = remoteReq.getContentLength();
        if (!remoteReq.isChunked() && cl != null && remoteReq.getContentLength() > 0) {
            remoteReq.setHeader("Transfer-Encoding", "chunked");
        }
        remoteReq.onHeaderLoaded();
    }

    /**
     *
     */
    public void fixLocalResponseHeader() throws IOException {
        String loc = localResp.getHead().getHeader1("Location");
        if (loc != null && loc.contains(remoteHost)) {
            if (loc.contains(remoteHostWithPort)) {
                loc = loc.replace(remoteHostWithPort, localHostWithPort);
            } else {
                loc = loc.replace(remoteHost, localHostWithPort);
            }
            if (localIsSecure != remoteIsSecure && loc.contains("http")) {
                if (!localIsSecure && loc.startsWith("https://")) {
                    loc = "http://" + loc.substring(8);
                } else if (localIsSecure && loc.startsWith("http://")) {
                    loc = "https://" + loc.substring(7);
                }
            }
            localResp.setHeader("Location", loc);
        }
    }
}
