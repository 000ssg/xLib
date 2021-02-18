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

import ssg.lib.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class HttpRequest extends HttpData {
    
    HttpResponse response;
    boolean closed = false;
    boolean client = false;
    private Map<String, Object> properties = new LinkedHashMap<>();
    
    public HttpRequest() {
    }
    
    public HttpRequest(String method, String path) {
        this.client = true;
        this.getHead().setProtocol(method, path, httpVersion);
    }
    
    public HttpRequest(boolean client) {
        this.client = client;
    }
    
    public HttpRequest(HttpRequest req, boolean client) throws IOException {
        super(req);
        getProperties().putAll(req.getProperties());
        this.client = client;
        this.closed = false;
    }
    
    public HttpRequest(ByteBuffer... data) throws IOException {
        this.client = false;
        if (data != null) {
            for (ByteBuffer bb : data) {
                add(bb);
            }
        }
    }
    
    public HttpRequest(Collection<ByteBuffer>... data) throws IOException {
        this.client = false;
        if (data != null) {
            add(data);
        }
    }

    /**
     * Builder-style variant of "add".
     *
     * @param data
     * @return
     * @throws IOException
     */
    public HttpRequest append(Collection<ByteBuffer>... data) throws IOException {
        long c = add(data);
        return this;
    }
    
    public HttpRequest append(ByteBuffer... data) throws IOException {
        long c = 0;
        if (data != null) {
            for (ByteBuffer bb : data) {
                if (bb != null) {
                    add(bb);
                }
            }
        }
        return this;
    }
    
    @Override
    public void onHeaderLoaded() {
        if (!getHead().completed) {
            getHead().completed = true;
        }
        if (response == null) {
            response = new HttpResponse(this);
        }
    }
    
    @Override
    public void onLoaded() {
        super.onLoaded();
        if (response == null) {
            response = new HttpResponse(this);
        }
    }

    /**
     * Response bound event callback
     *
     * @param resp
     */
    public void onResponseSent(HttpResponse resp) {
    }

    /**
     * Response bound event callback
     *
     * @param resp
     */
    public void onResponseHeaderSent(HttpResponse resp) {
    }

    /**
     * Response bound event callback
     *
     * @param resp
     */
    public void onResponseLoaded(HttpResponse resp) {
    }

    /**
     * Response bound event callback
     *
     * @param resp
     */
    public void onResponseHeaderLoaded(HttpResponse resp) {
    }
    
    public boolean closed() {
        return closed;
    }
    
    public void close() throws IOException {
        closed = true;
    }
    
    @Override
    public List<ByteBuffer> get() throws IOException {
        // in server mode returns response output, in server mode - self.
        lastSendTime = System.currentTimeMillis();
        return (client) ? super.get() : this.response.get();
    }
    
    @Override
    public boolean isSent() {
        return (client) ? super.isSent() : this.response.isSent();
    }
    
    @Override
    public void add(ByteBuffer bb) throws IOException {
        lastUpdateTime = System.currentTimeMillis();
        if (isCompleted()) {
            response.add(bb);
        } else {
            super.add(bb);
        }
    }
    
    public HttpResponse getResponse() {
        return response;
    }
    
    public void setHeader(String hn, String hv) throws IOException {
        if (!isCompleted()) {
            getHead().setHeader(hn, hv);
        } else if (getResponse() != null) {
            //getResponse().setHeader(hn, hv);
        }
    }
    
    public HttpSession getHttpSession() {
        return (getContext() instanceof HttpSession) ? (HttpSession) getContext() : null;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        int idx = sb.indexOf("{") + 1;
        sb.insert(idx, "\n  closed=" + closed);
        sb.insert(idx, "\n  client=" + client);
        if (response != null && response.getHead() != null && response.getHead().getProtocolInfo() != null && response.getHead().getProtocolInfo().length > 2) {
            sb.insert(idx, "\n  response=" + response.getHead().getProtocolInfo()[0] + " " + response.getHead().getProtocolInfo()[1] + " " + response.getHead().getProtocolInfo()[2]);
        }
        return sb.toString();
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    public String getHostURL() {
        String host = getHead().getHeader1("Host");
        String protocol = (isSecure()) ? "https://" : "http://";
        return protocol + host;
    }
    
    public String getQuery() {
        return (head.protocol != null) ? head.protocol[1] : null;
    }
    
    public boolean isDone() {
        if (client) {
            return getResponse() != null && getResponse().isCompleted() || closed;
        } else {
            return isSent() || closed;
        }
    }

    /**
     * Returns named parameter map or null if none.
     *
     * @return
     * @throws IOException
     */
    public Map<String, Object> getParameters() throws IOException {
        Map<String, Object> r = getMatcher().getParameters(null, false);
        if (canHaveFormParameters()) {
            r = getFormParameters(r);
        }
//        if (r == null) {
//            r = new LinkedHashMap<>();
//        }
        return r;
    }
    
    public boolean canHaveFormParameters() {
        return getBody() instanceof MultipartBody || getContentType() != null && getContentType().contains("www-form-urlencoded");
    }
    
    public Map<String, Object> getFormParameters(Map<String, Object> params) throws IOException {
        if (params == null) {
            params = new LinkedHashMap<String, Object>();
        }

        // add form-data parameters, if any
        if (getBody() instanceof MultipartBody) {
            MultipartBody mb = (MultipartBody) getBody();
            for (MultipartBody.Part part : mb.getParts()) {
                if (part.hasDisposition("form-data")) {
                    String s = part.getStringValue();
                    if (s == null) {
                        Object v = part.getValue();
                        params.put(part.name, v);
                    } else {
                        params.put(part.name, s);
                    }
                    
                }
            }
        } else if (getContentType() != null && getContentType().contains("www-form-urlencoded")) {
            try {
                String encoding = getContentCharset();
                if (encoding == null) {
                    encoding = "UTF-8"; // enforce some encoding for URLDecoder...
                }
                String[] fparams = BufferTools.toText(encoding, getBody().data()).split("&");
                for (String fparam : fparams) {
                    int idx = fparam.indexOf("=");
                    if (idx == -1) {
                        // param without value
                        String pn = URLDecoder.decode(fparam, encoding);
                        params.put(pn, null);
                    } else {
                        String pn = fparam.substring(0, idx);
                        String pv = fparam.substring(idx + 1);
                        try {
                            pn = URLDecoder.decode(pn, encoding);
                            pv = URLDecoder.decode(pv, encoding);
                        } catch (Throwable th) {
                            if (getContentCharset() == null) {
                                encoding = "ISO-8859-1"; // enforce default encoding for URLDecoder...
                                pn = URLDecoder.decode(pn, encoding);
                                pv = URLDecoder.decode(pv, encoding);
                            }
                        }
                        
                        if (1 == 1) {
                            toFormParameterValue(pn, pv, params);
                        } else {
                            if (pn.endsWith("[]")) {
                                List lst = (List) params.get(pn);
                                if (lst == null) {
                                    lst = new ArrayList();
                                    params.put(pn, lst);
                                }
                                lst.add(pv);
                            } else {
                                params.put(pn, pv);
                            }
                        }
                    }
                }
            } catch (Throwable th) {
                throw new IOException("Failed to parse www-form-urlencoded params: " + th, th);
            }
        }
        
        return params;
    }
    
    public static void toFormParameterValue(String pn, String pv, Object obj) {
        String[] pns = pn.split("\\[");
        Integer[] pnt = new Integer[pns.length];
        for (int i = 1; i < pns.length; i++) {
            pns[i] = pns[i].substring(0, pns[i].length() - 1);
            try {
                if (!pns[i].isEmpty()) {
                    pnt[i] = Integer.parseInt(pns[i]);
                }
            } catch (Throwable th) {
            }
        }
        if (pns.length == 1) {
            Map map = (Map) obj;
            map.put(pn, pv);
        } else {
            for (int i = 0; i < pns.length - 1; i++) {
                if (pns[i].isEmpty()) {
                    // terminal array value
                    int a = 0;
                } else if (pnt[i] != null) {
                    // intermediate indexed array value
                    if (obj instanceof List) {
                        List l = (List) obj;
                        if (pnt[i] < l.size()) {
                            obj = l.get(pnt[i]);
                        } else if (pnt[i] == l.size()) {
                            if (pns[i + 1].isEmpty() || pnt[i + 1] != null) {
                                obj = new ArrayList();
                                l.add(obj);
                            } else {
                                obj = new LinkedHashMap();
                                l.add(obj);
                            }
                        }
                    } else {
                        int a = 0;
                    }
                } else {
                    // intermediate/terminal object property
                    if (obj instanceof Map) {
                        Object oo = ((Map) obj).get(pns[i]);
                        if (oo == null) {
                            if (pns[i + 1].isEmpty() || pnt[i + 1] != null) {
                                oo = new ArrayList();
                            } else {
                                oo = new LinkedHashMap();
                            }
                            ((Map) obj).put(pns[i], oo);
                        }
                        obj = oo;
                        int a = 0;
                    } else if (obj instanceof List) {
                        int a = 0;
                    } else {
                        // ???
                        int a = 0;
                    }
                }
            }
        }
        int last = pns.length - 1;
        
        if (pns[last].isEmpty()) {
            if (obj instanceof Collection) {
                ((Collection) obj).add(pv);
            } else {
                int a = 0;
            }
        } else if (pnt[last] != null) {
            int a = 0;
        } else {
            if (obj instanceof Map) {
                ((Map) obj).put(pns[last], pv);
            } else {
                int a = 0;
            }
        }
    }

    /**
     * @return the properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }
}
