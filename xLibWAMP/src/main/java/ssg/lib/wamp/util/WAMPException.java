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
package ssg.lib.wamp.util;

import java.io.IOException;

/**
 *
 * @author 000ssg
 */
public class WAMPException extends IOException {

    String errorURI;

    public WAMPException() {
    }

    public WAMPException(String message) {
        super(message);
    }

    public WAMPException(String message, Throwable cause) {
        super(message, cause);
    }

    public WAMPException(Throwable cause) {
        super(cause);
    }

    public WAMPException(String message, String errorURI) {
        super(message);
        this.errorURI = errorURI;
    }

    public WAMPException(String message, Throwable cause, String errorURI) {
        super(message, cause);
        this.errorURI = errorURI;
    }

    public WAMPException(Throwable cause, String errorURI) {
        super(cause);
        this.errorURI = errorURI;
    }

    public String errorURI() {
        return errorURI;
    }
}
