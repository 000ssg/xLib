/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.http.dp.tokens;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import ssg.lib.common.CommonTools;
import ssg.lib.common.JSON;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpAuthenticator.UserVerifier;
import ssg.lib.http.HttpUser;
import static ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor.HEADER_SECRET;
import static ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor.TOKEN_EXPIRES_IN;
import static ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor.TOKEN_USER_ID;
import static ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor.TOKEN_USER_NAME;
import static ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor.TOKEN_USER_ROLES;
import static ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor.TOKEN_VALID;

/**
 *
 * @author 000ssg
 */
public class TokenUserVerifier implements UserVerifier {

    TokenVerifier tv;
    TokenPreVerifier tpv;
    URL tokenProvider;
    String tokenSecret;
    Map<String, Map<String, Object>> cachedTokens = new HashMap<>();
    // 5 minutes is default expiration period for token without "expires_in" info (to keep in cache)
    long undefinedExpirationLength = 1000 * 60 * 5;

    public TokenUserVerifier(TokenVerifier tv) {
        this.tv = tv;
    }

    public TokenUserVerifier(URL tokenProvider, TokenPreVerifier tpv) {
        this.tokenProvider = tokenProvider;
        this.tpv = tpv;
    }

    public TokenUserVerifier(URL tokenProvider, String tokenSecret, TokenPreVerifier tpv) {
        this.tokenProvider = tokenProvider;
        this.tokenSecret = tokenSecret;
        this.tpv = tpv;
    }

    public void clear() {
        try {
            cachedTokens.clear();
        } catch (Throwable th) {
        }
    }

    @Override
    public boolean canVerify(Object... parameters) {
        String auth = parameters != null && parameters.length == 1 && parameters[0] instanceof String ? ((String) parameters[0]) : null;
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        auth = auth.substring(auth.indexOf(" ") + 1);
        return tv != null ? tv.canVerify(auth) : tpv != null ? tpv.canVerify(auth) : false;
    }

    @Override
    public HttpAuthenticator.VerificationResult verify(Object... parameters) throws IOException {
        if (tv != null) {
            TokenVerifier.Token t = canVerify(parameters) ? tv.get((String) parameters[0]) : null;
            if (t != null) {
                return new HttpAuthenticator.VerificationResult(this, t.getToken_id(), t.getDomain(), t.getName()).addRoles(t.getRoles());
            }
        } else if (tokenProvider != null) {
            String tokenText = (String) parameters[0];
            tokenText = tokenText.substring(tokenText.indexOf(" ") + 1);

            Map<String, Object> token = null;
            Object valid = null;
            Long expires = null;

            synchronized (cachedTokens) {
                if (cachedTokens.containsKey(tokenText)) {
                    token = cachedTokens.get(tokenText);
                    if (token != null && token.get(TOKEN_EXPIRES_IN) instanceof Number) {
                        expires = ((Number) token.get(TOKEN_EXPIRES_IN)).longValue();
                    }
                    if (expires != null && expires > System.currentTimeMillis()) {
                        valid = true;
                    } else {
                        expires = null;
                        cachedTokens.remove(tokenText);
                    }
                }
            }
            if (valid == null) {
                HttpURLConnection conn = (HttpURLConnection) tokenProvider.openConnection();
                conn.setRequestProperty("Authorization", "" + parameters[0]);
                if (tokenSecret != null) {
                    conn.setRequestProperty(HEADER_SECRET, tokenSecret);
                }
                conn.setDoInput(true);
                conn.connect();
                Map<String, Object> map = null;
                try {
                    try ( InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();) {
                        String s = new String(CommonTools.loadInputStream(is), "UTF-8");
                        JSON.Decoder jd = new JSON.Decoder();
                        map = jd.readObject(s, Map.class);
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                conn.disconnect();
                valid = map != null ? map.get(TOKEN_VALID) : null;
                expires = map != null && map.get(TOKEN_EXPIRES_IN) instanceof Number ? System.currentTimeMillis() + ((Number) map.get(TOKEN_EXPIRES_IN)).intValue() * 1000 : null;
                synchronized (cachedTokens) {
                    map.put(tokenText, expires != null ? expires : System.currentTimeMillis() + 1000 * 60 * 5);
                    cachedTokens.put(tokenText, map);
                    token = map;
                }
            }
            if (valid instanceof Boolean && ((Boolean) valid) && (expires == null || System.currentTimeMillis() < expires)) {
                if (token != null) {
                    Map map = token;
                    HttpAuthenticator.VerificationResult r = new HttpAuthenticator.VerificationResult();
                    r.userId = (String) map.get(TOKEN_USER_ID);
                    r.userName = (String) map.get(TOKEN_USER_NAME);
                    r.userDomain = (String) map.get("iss");
                    if (expires != null) {
                        r.validTill = expires;
                    }
                    if (map.get(TOKEN_USER_ROLES) instanceof String) {
                        r.userRoles = ((String) map.get(TOKEN_USER_ROLES)).split(",");
                        for (int i = 0; i < r.userRoles.length; i++) {
                            r.userRoles[i] = r.userRoles[i].trim();
                        }
                    }
                    r.verifier = this;
                    return r;
                }
            } else {
                // invalid token
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> verifiedProperties(Object... parameters) {
        return canVerify(parameters) ? null : null;
    }

    @Override
    public boolean registerUser(String name, Object... parameters) {
        return false;
    }

    @Override
    public boolean unregisterUser(String name) {
        return false;
    }

    @Override
    public HttpUser.AUTH_TYPE getType() {
        return HttpUser.AUTH_TYPE.token;
    }

    public static interface TokenPreVerifier {

        boolean canVerify(String id);
    }
}
