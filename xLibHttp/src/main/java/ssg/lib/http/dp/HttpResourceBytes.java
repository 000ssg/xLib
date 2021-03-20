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
package ssg.lib.http.dp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import ssg.lib.common.Replacement;
import ssg.lib.common.buffers.ByteBufferPipeReplacement;
import ssg.lib.http.HttpResource;
import ssg.lib.http.base.HttpData;

/**
 *
 * @author 000ssg
 */
public class HttpResourceBytes implements HttpResource {

    long timestamp = System.currentTimeMillis();
    //byte[] data;
    VirtualData data;
    String path;
    String[] parameters;
    String[] localizeable;

    public HttpResourceBytes(VirtualData data, String path) {
        this.data = data;
        this.path = path;
    }

    public HttpResourceBytes(byte[] data, String path, String contentType) {
        //this.data = data;
        this.data = new ActualData(data, contentType);
        this.path = path;
    }

    public HttpResourceBytes(URL url, String path, String contentType) throws IOException {
        //this.data = cacheInputStream(url.openStream());
        this.data = new ActualData(url.openStream(), contentType);
        this.path = path;
    }

    public HttpResourceBytes(InputStream is, String path, String contentType) throws IOException {
        //this.data = cacheInputStream(is);
        this.data = new ActualData(is, contentType);
        this.path = path;
    }

    byte[] cacheInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int c = 0;
        while ((c = is.read(buf)) != -1) {
            os.write(buf, 0, c);
        }
        is.close();
        os.close();
        return os.toByteArray();
    }

    public HttpResourceBytes(String data, String path, String contentType) throws IOException {
        this.path = path;
        if (contentType == null) {
            contentType = "text/plain; encoding=utf-8";
        }
        int idx = contentType.toLowerCase().indexOf("encoding");
        String encoding = "UTF-8";
        if (idx != -1) {
            idx = contentType.indexOf("=", idx);
            if (idx != -1) {
                encoding = contentType.substring(idx + 1).trim();
                idx = encoding.indexOf(";");
                if (idx != -1) {
                    encoding = encoding.substring(0, idx).trim();
                }
            }
        } else {
            contentType += "; encoding=" + encoding;
        }

        this.data = new ActualData(data.getBytes(encoding), contentType);
    }

    @Override
    public HttpResource find(String path) {
        return path != null && path.equals(path) ? this : null;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String contentType(HttpData httpData) {
        return data.getContentType(this, httpData);
    }

    @Override
    public long size(HttpData httpData) {
        return (data != null) ? data.get(this, httpData).length : 0;
    }

    @Override
    public InputStream open(HttpData httpData, Replacement... replacements) throws IOException {
        return new ByteArrayInputStream(data(httpData));
    }

    @Override
    public byte[] data(HttpData httpData, Replacement... replacements) throws IOException {
        if (data == null || replacements == null || replacements.length == 0 || replacements[0] == null) {
            return data.get(this, httpData);
        } else {
            ByteBufferPipeReplacement pipe = new ByteBufferPipeReplacement(replacements);
            pipe.write(ByteBuffer.wrap(data.get(this, httpData)));
            pipe.close();
            byte[] buf = new byte[(int) pipe.getLength()];
            pipe.close();
            int c = pipe.read(ByteBuffer.wrap(buf));
            if (c != buf.length) {
                int a = 0;
            }
            return buf;
        }
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public Long expires() {
        return null;
    }

    public void timestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String[] parameters() {
        return parameters;
    }

    public void parameters(String[] parameters) {
        this.parameters = parameters;
    }

    @Override
    public String[] localizeable() {
        return localizeable;
    }

    @Override
    public void localizeable(String[] localizeable) {
        this.localizeable = localizeable;
    }

    @Override
    public boolean requiresInitialization(HttpData httpData) {
        return false;
    }

    /**
     * Binary data delegator. Used to evaluate/select data based on given
     * context.
     */
    public static interface VirtualData {

        default String getContentType(HttpResourceBytes owner, HttpData httpData) {
            return "text/javascript; encoding=utf-8";
        }

        byte[] get(HttpResourceBytes owner, HttpData httpData);
    }

    /**
     * Static data provider.
     */
    public class ActualData implements VirtualData {

        String contentType;
        byte[] data;

        public ActualData(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        public ActualData(InputStream is, String contentType) throws IOException {
            data = cacheInputStream(is);
            this.contentType = contentType;
        }

        @Override
        public byte[] get(HttpResourceBytes owner, HttpData httpData) {
            return data;
        }

        @Override
        public String getContentType(HttpResourceBytes owner, HttpData httpData) {
            return contentType != null ? contentType : VirtualData.super.getContentType(owner, httpData);
        }
    }
}
