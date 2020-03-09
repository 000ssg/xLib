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

import static ssg.lib.oauth2.OAuthConstants.TR_AUTHORIZATION_CODE;

/**
 * Explicit authorization token grant request. Note: not used for implicit
 * where token is returned in 1 step.
 *
 * REF: https://tools.ietf.org/html/rfc6749#section-4.1.3
 */
public class AuthorizationCodeTokenRequest extends TokenRequest {

    //mandatory
    public String code;
    //conditional
    public String redirect_uri;
    //conditional
    public String client_id;

    public AuthorizationCodeTokenRequest() {
        grant_type = TR_AUTHORIZATION_CODE;
    }
    
}
