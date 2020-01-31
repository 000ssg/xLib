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
package ssg.lib.oauth.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import ssg.lib.oauth.OAuthAuthorizationServer;
import ssg.lib.oauth.OAuthAuthorizationServer.AuthRequest;
import static ssg.lib.oauth.OAuthAuthorizationServer.AuthRequest.RESPONSE_TYPE.code;
import static ssg.lib.oauth.OAuthAuthorizationServer.AuthRequest.RESPONSE_TYPE.token;
import ssg.lib.oauth.OAuthAuthorizationServer.AuthResponse;
import ssg.lib.oauth.OAuthAuthorizationServer.ErrorResponse;
import ssg.lib.oauth.OAuthAuthorizationServer.ErrorResponse.ERROR_CODE;
import ssg.lib.oauth.OAuthAuthorizationServer.Request;
import ssg.lib.oauth.OAuthAuthorizationServer.Response;
import static ssg.lib.oauth.OAuthAuthorizationServer.TR_AUTHORIZATION_CODE;
import static ssg.lib.oauth.OAuthAuthorizationServer.TR_CLIENT_CREDENTIALS;
import static ssg.lib.oauth.OAuthAuthorizationServer.TR_PASSWORD;
import static ssg.lib.oauth.OAuthAuthorizationServer.TR_REFRESH;
import ssg.lib.oauth.OAuthAuthorizationServer.TokenRequest;
import ssg.lib.oauth.OAuthAuthorizationServer.TokenResponse;

/**
 *
 * @author 000ssg
 */
public class OAuthAuthorizationServerImpl implements OAuthAuthorizationServer {

    ThreadLocal context = new ThreadLocal();
    Map<String, OAuthTokenInfo> tokens = new HashMap<String, OAuthTokenInfo>();

    @Override
    public Response authorize(AuthRequest request) {
        OAuthRequestContext context = getContext();
        if (context != null && request != null && request.response_type != null) {
            switch (request.response_type) {
                case code:
                    return authorizationCodeGrant(context, request);
                case token:
                    return implicitGrant(context, request);
                default:
            }
        }
        return new ErrorResponse(ERROR_CODE.invalid_request);
    }

    @Override
    public Response token(TokenRequest request) {
        OAuthRequestContext context = getContext();
        if (context != null && request != null && request.grant_type != null) {
            if (TR_AUTHORIZATION_CODE.equals(request.grant_type)) {
                OAuthTokenInfo oati = tokens.get(clientKey(request));
            } else if (TR_PASSWORD.equals(request.grant_type)) {
            } else if (TR_CLIENT_CREDENTIALS.equals(request.grant_type)) {
            } else if (TR_REFRESH.equals(request.grant_type)) {
            }
        }

        return new ErrorResponse(ERROR_CODE.invalid_request);
    }

    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////// utilities
    ///////////////////////////////////////////////////////////////////////
    /**
     * Context getter. Context provides any additional info needed for
     * identifying caller within any method during request
     *
     * @return
     */
    public OAuthRequestContext getContext() {
        return (OAuthRequestContext) context.get();
    }

    /**
     * Set context for caller's request.
     *
     * @param context
     */
    public void setContext(OAuthRequestContext context) {
        this.context.set(context);
    }

    String clientKey(Request request) {
        OAuthRequestContext context = getContext();
        if (context != null) {
            return context.remoteAddress;
        } else {
            return null;
        }
    }

    /**
     * For extensions. By default returns provided response object.
     *
     * @param context
     * @param request
     * @param response
     * @return
     */
    public Response verifyAuth(OAuthRequestContext context, OAuthTokenInfo oati, AuthRequest request, Response response) {
        return response;
    }

    /**
     * For extensions. By default returns provided response object.
     *
     * @param context
     * @param request
     * @param response
     * @return
     */
    public Response verifyToken(OAuthRequestContext context, OAuthTokenInfo oati, Request request, Response response) {
        return response;
    }

    /**
     * Generates token based on context and token info.
     *
     * @param context
     * @param oati
     * @return
     */
    public TokenResponse generateToken(OAuthRequestContext context, OAuthTokenInfo oati) {
        boolean refreshGenerated = false;
        oati.token = UUID.randomUUID().toString();
        if (oati.refreshToken == null) {
            oati.refreshToken = UUID.randomUUID().toString();
            refreshGenerated = true;
        }

        TokenResponse tr = new TokenResponse();
        tr.access_token = oati.token;
        tr.expires_in = (int) (System.currentTimeMillis() - oati.expiresAt);
        if (refreshGenerated) {
            tr.refresh_token = oati.refreshToken;
        }
        tr.scope = oati.scope;
        tr.state = oati.state;

        return tr;
    }

    ///////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////// implementation
    ///////////////////////////////////////////////////////////////////////
    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.1
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.2
     *
     * @param request
     * @return
     */
    public Response authorizationCodeGrant(OAuthRequestContext context, AuthRequest request) {
        OAuthTokenInfo oati = new OAuthTokenInfo();
        oati.clientId = request.client_id;
        oati.remoteAddress = context.remoteAddress;
        oati.code = UUID.randomUUID().toString();
        oati.codeExpiresAt = context.getAuthCodeExpiration() + System.currentTimeMillis();
        oati.scope = request.scope;
        oati.state = request.state;
        tokens.put(clientKey(request), oati);

        AuthResponse ar = new AuthResponse();
        ar.code = oati.code;
        ar.state = request.state;

        Response r = verifyAuth(context, oati, request, ar);

        return r;
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.2
     *
     * @param request
     * @return
     */
    public Response implicitGrant(OAuthRequestContext context, AuthRequest request) {
        OAuthTokenInfo oati = new OAuthTokenInfo();
        oati.clientId = request.client_id;
        oati.remoteAddress = context.remoteAddress;
        oati.codeExpiresAt = context.getAuthCodeExpiration() + System.currentTimeMillis();
        oati.scope = request.scope;
        oati.state = request.state;
        tokens.put(clientKey(request), oati);

        TokenResponse tr = generateToken(context, oati);

        Response r = verifyToken(context, oati, request, tr);

        return r;
    }

    /**
     * Context is provided by OAuith container to pass HTTP request based info
     */
    public static class OAuthRequestContext {

        // HTTP
        public String method;
        public String uri;
        public String protocol;
        public Map<String, String[]> headers;
        // IP
        public String remoteAddress;
        public String localAddress;
        // defaults
        public String defaultScope = "";
        public long authExpiration = 1000 * 60; // 1 minute for Auth code to use...

        public String evaluateScope(String scopes) {
            String scope = null;
            String[] ss = (scopes != null) ? scopes.split(" ") : new String[0];
            for (String s : ss) {
                if (scope == null && s != null && !s.trim().isEmpty()) {
                    scope = s.trim();
                    break;
                }
            }
            if (scope == null) {
                scope = defaultScope;
            }
            return scope;
        }

        public long getAuthCodeExpiration() {
            return authExpiration;
        }
    }

    public static class OAuthTokenInfo {

        public String clientId;
        public String remoteAddress;
        public String token;
        public long expiresAt;
        public String refreshToken;
        public long refreshExpiresAt;
        public String scope;
        // support values, used to identify token reason etc.
        String code;
        Long codeExpiresAt;
        String redirectURI;
        String username;
        String password;
        String state;
    }
}
