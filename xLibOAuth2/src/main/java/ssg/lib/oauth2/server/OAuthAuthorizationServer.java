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
package ssg.lib.oauth2.server;

/**
 *
 * @author 000ssg
 */
public interface OAuthAuthorizationServer {
    /**
     * Depending on requested response type (code or token) returns AuthResponse
     * or TokenResponse. Returns ErrorResponse if error.
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.2
     *
     * @param request
     * @return
     */
    Response authorize(AuthRequest request);

    /**
     * Returns TokenResponse or ErrorResponse.
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.3
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.3
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.4
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.5
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-6
     *
     * @param request
     * @return
     */
    Response token(TokenRequest request);

}
