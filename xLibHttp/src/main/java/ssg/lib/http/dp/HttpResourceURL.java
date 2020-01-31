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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.common.CommonTools;
import ssg.lib.common.ImagingTools;
import ssg.lib.common.InputStreamReplacement;
import ssg.lib.common.Replacement;
import ssg.lib.http.HttpResource;
import ssg.lib.http.base.HttpData;

/**
 *
 * @author 000ssg
 */
public class HttpResourceURL implements HttpResource {

    public static Map<String, byte[]> scaledImagesCache = new LinkedHashMap<>();

    public static final long NO_TIMESTAMP = System.currentTimeMillis();

    public HttpResourceURL(URL url, String path, String contentType, Long timestamp) {
        this.url = url;
        this.path = path;
        this.contentType = contentType;
        this.timestamp = (timestamp != null) ? timestamp : NO_TIMESTAMP;
    }
    URL url;
    String path;
    String contentType;
    long timestamp;
    String[] parameters;
    String[] localizeable;

    @Override
    public HttpResource find(String path) {
        if (path != null && path.equals(path)) {
            return this;
        }
        return null;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public long size() {
        return (url != null) ? HttpStaticDataProcessor.UNKNOWN_SIZE : 0;
    }

    @Override
    public InputStream open(HttpData httpData, Replacement... replacements) throws IOException {
        InputStream is = null;
        if (url != null && contentType.contains("image/")) {
            if (httpData != null && httpData.getMatcher() != null) {
                String w = httpData.getMatcher().getQueryPathParameter("w");
                String h = httpData.getMatcher().getQueryPathParameter("h");
                String f = httpData.getMatcher().getQueryPathParameter("f");
                if (w == null) {
                    w = httpData.getMatcher().getQueryPathParameter("width");
                }
                if (h == null) {
                    h = httpData.getMatcher().getQueryPathParameter("height");
                }
                if (f == null) {
                    f = httpData.getMatcher().getQueryPathParameter("format");
                }
                if (w != null || h != null || f != null) {
                    try {
                        Integer wi = (w != null) ? Integer.parseInt(w) : null;
                        Integer hi = (h != null) ? Integer.parseInt(h) : null;
                        byte[] bb = ImagingTools.scaleImageTo(url, wi, hi, f, scaledImagesCache);
                        if (bb != null && bb.length > 0) {
                            is = new ByteArrayInputStream(bb);
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        }

        if (is == null) {
            is = url != null ? url.openStream() : null;
        }

        if (is != null && replacements != null && replacements.length > 0 && replacements[0] != null) {
            is = new InputStreamReplacement(is, replacements);
        }
        return is;
    }

    @Override
    public byte[] data(HttpData httpData, Replacement... replacements) throws IOException {
        InputStream is = open(httpData, replacements);
        byte[] buf = CommonTools.loadInputStream(is);
        return buf;
    }

    @Override
    public long timestamp() {
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            // update timestamp...
            try {
                File f = new File(url.toURI());
                timestamp = f.lastModified();
            } catch (Throwable th) {
            }
        }
        return timestamp;
    }

    @Override
    public Long expires() {
        return null;
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
        if (url != null && contentType.contains("image/")) {
            if (httpData != null && httpData.getMatcher() != null) {
                String w = httpData.getMatcher().getQueryPathParameter("w");
                String h = httpData.getMatcher().getQueryPathParameter("h");
                String f = httpData.getMatcher().getQueryPathParameter("f");
                if (w == null) {
                    w = httpData.getMatcher().getQueryPathParameter("width");
                }
                if (h == null) {
                    h = httpData.getMatcher().getQueryPathParameter("height");
                }
                if (f == null) {
                    f = httpData.getMatcher().getQueryPathParameter("format");
                }
                if (w != null || h != null || f != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
