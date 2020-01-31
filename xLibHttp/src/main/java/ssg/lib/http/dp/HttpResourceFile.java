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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import ssg.lib.common.CommonTools;
import ssg.lib.common.InputStreamReplacement;
import ssg.lib.common.Replacement;
import ssg.lib.http.HttpResource;
import ssg.lib.http.base.HttpData;

/**
 *
 * @author 000ssg
 */
public class HttpResourceFile implements HttpResource {

    public static final long NO_TIMESTAMP = System.currentTimeMillis();

    public HttpResourceFile(File file, String path, String contentType) {
        this.file = file;
        this.path = path;
        this.contentType = contentType;
    }
    File file;
    String path;
    String contentType;
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
        return (file != null && file.isFile()) ? file.length() : 0;
    }

    @Override
    public InputStream open(HttpData httpData, Replacement... replacements) throws IOException {
        InputStream is = file != null && file.isFile() ? file.toURI().toURL().openStream() : null;
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
        return (file != null) ? file.lastModified() : NO_TIMESTAMP;
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
        return false;
    }
}
