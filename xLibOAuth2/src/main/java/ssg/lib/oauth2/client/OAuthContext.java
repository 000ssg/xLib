/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
package ssg.lib.oauth2.client;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Represents OAuth info
 */
public interface OAuthContext {

    /**
     * Authentication domain name to distinguish oauth-only accounts.
     *
     * @return
     */
    String domain();

    String title();

    // owner
    <A extends OAuthClient> A getOAuth();

    <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException;

    // Authentication URL builder
    URL getAuthURL() throws IOException;

    /**
     * Check if given authData are related to this authentication
     * session/context.
     *
     * @param authData
     * @return
     * @throws IOException
     */
    boolean checkAuthData(Map<String, Object> authData) throws IOException;

    /**
     * Request authorization token
     *
     * @param code
     * @param authData
     * @return
     * @throws IOException
     */
    String getAuthToken(String code, Map<String, Object> authData) throws IOException;

    /**
     * Do logout...
     *
     * @return
     * @throws IOException
     */
    boolean revokeAuthToken() throws IOException;

    // token/auth properties
    String code();

    String idToken();

    String accessToken();

    String refreshToken();

    long expiresAt();

    String tokenType();

    String[] scope();

    void setProperty(String name, Object value);

    <T> T getProperty(String name);

    void reset();
    
}
