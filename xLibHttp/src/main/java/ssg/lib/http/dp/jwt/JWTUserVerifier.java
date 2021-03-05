/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.http.dp.jwt;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpAuthenticator.UserVerifier;
import ssg.lib.http.HttpUser;

/**
 *
 * @author sesidoro
 */
public class JWTUserVerifier implements UserVerifier {

    JWT jwt;
    URL jwtProvider;
    String jwtSecret;

    public JWTUserVerifier() {
        jwt = new JWT();
    }

    public JWTUserVerifier(JWT jwt) {
        this.jwt = jwt != null ? jwt : new JWT();
    }

    public JWTUserVerifier(URL jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    public JWTUserVerifier(URL jwtProvider, String jwtSecret) {
        this.jwtProvider = jwtProvider;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public boolean canVerify(Object... parameters) {
        return parameters != null && parameters.length == 1 && parameters[0] instanceof String && ((String) parameters[0]).split("\\.").length == 3;
    }

    @Override
    public HttpAuthenticator.VerificationResult verify(Object... parameters) throws IOException {
        if (jwt != null) {
            JWT.Token t = canVerify(parameters) ? jwt.get((String) parameters[0]) : null;
            if (t != null) {
                return new HttpAuthenticator.VerificationResult(this, t.id, t.name, t.iss);
            }
        } else if (jwtProvider != null) {
            HttpURLConnection conn = (HttpURLConnection) jwtProvider.openConnection();
            conn.setRequestProperty("Authorization", "Bearing " + parameters[0]);
            conn.connect();
            Map<String, Object> map = (Map) conn.getContent();
            Object v = map.get("result");
            if (v instanceof Boolean && ((Boolean) v)) {
                Map<String, Object>[] maps = JWT.decodeComponents((String) parameters[0]);
                if (maps != null && maps.length > 1) {
                    map = maps[1];
                    HttpAuthenticator.VerificationResult r = new HttpAuthenticator.VerificationResult();
                    r.userId = (String) map.get("id");
                    r.userName = (String) map.get("name");
                    r.userDomain = (String) map.get("iss");
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
        return canVerify(parameters) ? jwt.decodeComponents((String) parameters[0])[1] : null;
    }

    @Override
    public boolean registerUser(String name, Object... parameters) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean unregisterUser(String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public HttpUser.AUTH_TYPE getType() {
        return HttpUser.AUTH_TYPE.token;
    }

}
