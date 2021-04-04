/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.http_cs;

import java.io.IOException;
import java.net.URI;
import ssg.lib.common.Config;
import ssg.lib.http.dp.tokens.TokenUserVerifier;

/**
 *
 * @author sesidoro
 */
public class AuthAdapter {
    
    public AuthAdapterConf createAuthadapterConf(String text) {
        return new AuthAdapterConf(text.startsWith("{") || text.startsWith("[") ? new String[]{text} : text.split(";"));
    }
    
    public TokenUserVerifier createUserVerifier(AuthAdapterConf conf) throws IOException {
        final String type = conf.type;
        final String prefix = conf.tokenPrefix;
        return new TokenUserVerifier(
                conf.uri.toURL(),
                conf.secret,
                id -> {
                    return "jwt".equalsIgnoreCase(type) ? id != null && id.split("\\.").length == 3 : id != null && id.startsWith(prefix);
                }
        );
    }
    
    public static class AuthAdapterConf extends Config {
        
        public AuthAdapterConf() {
            super("");
            noSysProperties();
        }
        
        public AuthAdapterConf(String... args) {
            super("");
            noSysProperties();
            Config.load(this, args);
        }
        @Description("Token type: jwt or token")
        public String type;
        @Description("URI to token verification service")
        public URI uri;
        @Description("Secret to pass to verify token")
        public String secret;
        @Description("HTTP header to pass token verifier secret. Default is 'App-Secret'")
        public String secretHeader;
        @Description("Authentication type. Defaults to 'Bearer'")
        public String authType = "Bearer";
        @Description("Token distinction rule: if defined this verifie is applied only if prefix matches.")
        public String tokenPrefix;
    }
}
