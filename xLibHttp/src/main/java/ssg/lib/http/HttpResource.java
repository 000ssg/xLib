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
package ssg.lib.http;

import java.io.IOException;
import java.io.InputStream;
import ssg.lib.common.Replacement;
import ssg.lib.http.base.HttpData;

/**
 *
 * @author 000ssg
 */
public interface HttpResource {

    HttpResource find(String path);

    String path();

    String contentType();

    /**
     * Enable context-specific size...
     * @param httpData
     * @return 
     */
    long size(HttpData httpData);

    InputStream open(HttpData httpData, Replacement... replacements) throws IOException;

    byte[] data(HttpData httpData, Replacement... replacements) throws IOException;

    long timestamp();

    Long expires();

    /**
     * Parameters refer to structural and system elements. Locale-specifics is
     * handled via localizeable...
     *
     * @return
     */
    String[] parameters();

    void parameters(String[] parameters);

    /**
     * Localizeable are used to indicate locale-specific replacements.
     *
     * @return
     */
    String[] localizeable();

    void localizeable(String[] localizeable);

    boolean requiresInitialization(HttpData httpData);
}
