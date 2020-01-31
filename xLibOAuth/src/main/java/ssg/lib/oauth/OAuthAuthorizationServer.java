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
package ssg.lib.oauth;

import java.util.Map;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XOption;
import ssg.lib.http.rest.annotations.XOption.REQUIRE_TYPE;
import ssg.lib.http.rest.annotations.XParameter;
import ssg.lib.http.rest.annotations.XType;

/**
 *
 * @author 000ssg
 */
@XType
public interface OAuthAuthorizationServer {

    public static final String TR_AUTHORIZATION_CODE = "authorization_code";
    public static final String TR_PASSWORD = "password";
    public static final String TR_CLIENT_CREDENTIALS = "client_credentials";
    public static final String TR_REFRESH = "refresh_token";

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
    @XMethod
    Response authorize(@XParameter(name = "request") AuthRequest request);

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
    @XMethod
    Response token(@XParameter(name = "request") TokenRequest request);

    ////////////////////////////////////////////////////////////////
    /////////////////////////////////////// Requests
    ////////////////////////////////////////////////////////////////
    /**
     * Root for Auth and Token requests.
     */
    public static interface Request {
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.1
     */
    public static class AuthRequest implements Request {

        public static enum RESPONSE_TYPE {
            code,
            token
        }

        @XOption(require = REQUIRE_TYPE.mandatory)
        public RESPONSE_TYPE response_type;

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String client_id;

        @XOption(require = REQUIRE_TYPE.optional)
        public String redirect_uri;

        @XOption(require = REQUIRE_TYPE.optional)
        public String scope;

        @XOption(require = REQUIRE_TYPE.recommended)
        public String state;
    }

    /**
     * Generic token request: common for
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.3
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.3.2
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.4.2
     *
     *
     */
    public static class TokenRequest implements Request {

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String grant_type;
    }

    /**
     * Explicit authorization token grant request. Note: not used for implicit
     * where token is returned in 1 step.
     *
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.3
     */
    public static class AuthorizationCodeTokenRequest extends TokenRequest {

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String code;

        @XOption(require = REQUIRE_TYPE.conditional)
        public String redirect_uri;

        @XOption(require = REQUIRE_TYPE.conditional)
        public String client_id;

        public AuthorizationCodeTokenRequest() {
            grant_type = TR_AUTHORIZATION_CODE;//"authorization_code";
        }
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.3.2
     */
    public static class PasswordCredentialsTokenRequest extends TokenRequest {

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String username;

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String password;

        public PasswordCredentialsTokenRequest() {
            grant_type = TR_PASSWORD;//"password";
        }
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.4.2
     */
    public static class ClientCredentialsTokenRequest extends TokenRequest {

        @XOption(require = REQUIRE_TYPE.optional)
        public String scope;

        public ClientCredentialsTokenRequest() {
            grant_type = TR_CLIENT_CREDENTIALS; //"client_credentials";
        }
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.5
     */
    public static class ExtensionTokenRequest extends TokenRequest {

        public ExtensionTokenRequest() {
        }

        public ExtensionTokenRequest(String ext_grant_type) {
            grant_type = ext_grant_type;
        }
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-6
     */
    public static class RefreshTokenRequest extends TokenRequest {

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String refresh_token;

        @XOption(require = REQUIRE_TYPE.optional)
        public String scope;

        public RefreshTokenRequest() {
            grant_type = TR_REFRESH; //"refresh_token";
        }
    }

    ////////////////////////////////////////////////////////////////
    /////////////////////////////////////// Responses
    ////////////////////////////////////////////////////////////////
    public static abstract class Response {

        @XOption(require = REQUIRE_TYPE.conditional)
        public String state;
    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-5.2
     */
    public static class ErrorResponse extends Response {

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

        @XOption(require = REQUIRE_TYPE.mandatory)
        public ERROR_CODE error;

        @XOption(require = REQUIRE_TYPE.optional)
        public String error_description;

        @XOption(require = REQUIRE_TYPE.optional)
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

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.1.2
     */
    public static class AuthResponse extends Response {

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String code;

    }

    /**
     * REF: https://tools.ietf.org/html/rfc6749#section-4.2.2
     */
    public static class TokenResponse extends Response {

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String access_token;

        @XOption(require = REQUIRE_TYPE.mandatory)
        public String token_type;

        @XOption(require = REQUIRE_TYPE.recommended)
        public Integer expires_in;

        @XOption(require = REQUIRE_TYPE.optional)
        public String refresh_token;

        @XOption(require = REQUIRE_TYPE.optional)
        public String scope;

        @XOption(require = REQUIRE_TYPE.unrecognized)
        public Map<String, String> unrecognized;
    }

}
