/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
package ssg.lib.oauth2.server;

/**
 * REF: https://tools.ietf.org/html/rfc6749#section-5.2
 */
public class ErrorResponse extends Response {

    public static enum ERROR_CODE {
        invalid_request,
        unauthorized_client,
        access_denied,
        unsupported_response_type,
        invalid_scope,
        server_error,
        temporarily_unavailable,
        other // to represent any extension... not standard...
    }
    //mandatory
    public ERROR_CODE error;
    //optional
    public String error_description;
    //optional
    public String error_uri;

    public ErrorResponse() {
    }

    public ErrorResponse(ERROR_CODE error) {
        this.error = error;
    }

    public ErrorResponse(ERROR_CODE error, String error_description, String error_uri) {
        this.error = error;
        this.error_description = error_description;
        this.error_uri = error_uri;
    }

}
