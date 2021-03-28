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
package ssg.lib.http.dp.tokens;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import ssg.lib.common.JSON;
import ssg.lib.common.Refl;
import ssg.lib.http.HttpSession;
import ssg.lib.http.HttpUser;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.dp.tokens.TokenAuthenticator;
import ssg.lib.http.dp.tokens.TokenVerifier;

/**
 *
 * @author 000ssg
 */
public class JWTTokenVerifier extends TokenVerifier implements TokenAuthenticator {

    // HTTP header names
    public static String AUTH_HEADER = "JWT-Token";
//    public static String JWT_HEADER_SECRET = "JWT-Secret";
    // Token validation fields
//    public static String JWT_VALID = "valid"; // boolean
//    public static String JWT_EXPIRES_IN = "expires_in"; // int, seconds

    // defaults
    String sub = "test";
    String iss = "self";
    long exp = 1000 * 60 * 15; // default expiration = 15 min

    // JSON support
    static Refl refl = new Refl.ReflJSON();
    static JSON.Encoder encoder = new JSON.Encoder(refl);
    static JSON.Decoder decoder = new JSON.Decoder(refl);

    // signer support
    SecureRandom secureRandom;
    KeyPair keyPair;
    // header
    String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    public JWTTokenVerifier() {
        try {
            secureRandom = new SecureRandom();
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException nsaex) {
            nsaex.printStackTrace();
        }
    }

    @Override
    public String type() {
        return "jwt";
    }

    @Override
    public JWTTokenVerifier configureStore(TokenStore store) {
        super.configureStore(store);
        return this;
    }

    @Override
    public <T extends Token> T create() {
        return (T) new JWTToken(this);
    }

    @Override
    public String toText(Token token) {
        StringBuilder sb = new StringBuilder();
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate(), secureRandom);

            String head1B64 = Base64.getEncoder().encodeToString(header.getBytes("UTF-8"));
            String payload1B64 = Base64.getEncoder().encodeToString(encoder.writeObject(token.toMap()).getBytes("UTF-8"));
            signature.update(head1B64.getBytes());
            signature.update(payload1B64.getBytes());
            String signature1B64 = Base64.getEncoder().encodeToString(signature.sign());

            sb.append(head1B64);
            sb.append('.');
            sb.append(payload1B64);
            sb.append('.');
            sb.append(signature1B64);
        } catch (NoSuchAlgorithmException nsaex) {
            nsaex.printStackTrace();
        } catch (InvalidKeyException ikex) {
            ikex.printStackTrace();
        } catch (UnsupportedEncodingException ueex) {
            ueex.printStackTrace();
        } catch (SignatureException siex) {
            siex.printStackTrace();
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
        return sb.toString();
    }

    @Override
    public boolean canVerify(String id) {
        return id != null && id.split("\\.").length == 3;
    }

    /**
     * Used to convert JWT text into token instance.
     *
     * @param <T>
     * @param tokenS
     * @return
     */
    <T extends Token> T toToken(String jwtToken) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");

            String[] split_string = jwtToken.split("\\.");
            String base64EncodedHeader = split_string[0];
            String base64EncodedBody = split_string[1];
            String base64EncodedSignature = split_string[2];

            String header = new String(Base64.getDecoder().decode(base64EncodedHeader), "UTF-8");
            String body = new String(Base64.getDecoder().decode(base64EncodedBody), "UTF-8");
            byte[] jwtSignature = Base64.getDecoder().decode(base64EncodedSignature);

            signature.initVerify(keyPair.getPublic());
            signature.update(base64EncodedHeader.getBytes());
            signature.update(base64EncodedBody.getBytes());
            boolean ok = signature.verify(jwtSignature);

            if (ok) {
                T t = create();
                t.fromMap(decoder.readObject(body, Map.class));
                takeOwnership(t);
                return t;
            }
        } catch (NoSuchAlgorithmException nsaex) {
            nsaex.printStackTrace();
        } catch (InvalidKeyException ikex) {
            ikex.printStackTrace();
        } catch (UnsupportedEncodingException ueex) {
            ueex.printStackTrace();
        } catch (SignatureException siex) {
            siex.printStackTrace();
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
        return null;
    }

    /**
     * Returns array of Map [header,payload]
     *
     * @param jwtToken
     * @return
     */
    public static Map<String, Object>[] decodeComponents(String jwtToken) {
        try {
            String[] split_string = jwtToken.split("\\.");
            String base64EncodedHeader = split_string[0];
            String base64EncodedBody = split_string[1];

            String header = new String(Base64.getDecoder().decode(base64EncodedHeader), "UTF-8");
            String body = new String(Base64.getDecoder().decode(base64EncodedBody), "UTF-8");
            return new Map[]{decoder.readObject(header, Map.class), decoder.readObject(body, Map.class)};
        } catch (UnsupportedEncodingException ueex) {
            ueex.printStackTrace();
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
        return null;
    }

    @Override
    public Token createToken(String id, String domain, String name, String roles, Long expires, Object... extra) {
        Token t = super.createToken(id, domain, name, roles, expires, extra);
        return t;
    }

    @Override
    public void updateToken(Token token, Object... extra) {
        super.updateToken(token, extra);
        JWTToken jt = (JWTToken) token;
        jt.sub = this.sub;
        jt.iss = this.iss;
    }

    ////////////////////////////////////////////////////////////////////////////
    @Override
    public boolean canAuthenticate(HttpRequest req) {
        return req != null && req.getHttpSession() != null && req.getHttpSession().getUser() != null && req.getHttpSession().getUser().getId() != null;
    }

    @Override
    public String doAuthentication(HttpRequest req) {
        HttpSession session = req.getHttpSession();
        HttpUser user = session.getUser();
        if (user != null && !"jwt".equals(user.getProperties().get("type"))) {
            List<Token> ts = find(null, user.getDomainName(), user.getId(), user.getName());
            for (Token t : ts) {
                switch (t.verify()) {
                    case valid:
                        return t.toText();
                }
            }

            List<String> roles = user.getRoles();
            StringBuilder rls = new StringBuilder();
            if (roles != null && !roles.isEmpty()) {
                for (String rl : roles) {
                    if (rl == null || rl.isEmpty()) {
                        continue;
                    }
                    if (rls.length() > 0) {
                        rls.append(',');
                    }
                    rls.append(rl);
                }
            }
            Token t = this.createToken(user.getId(), user.getDomainName(), user.getName(), rls.length() > 0 ? rls.toString() : null, null);
            return t != null ? t.toText() : null;
        }
        return null;
    }

    @Override
    public String authHeaderName() {
        return AUTH_HEADER;
    }

    public static class JWTToken extends Token {

        private String sub;
        private String iss;

        public JWTToken() {
        }

        public JWTToken(TokenVerifier tv) {
            super(tv);
        }

        /**
         * @return the sub
         */
        public String getSub() {
            return sub;
        }

        /**
         * @return the iss
         */
        public String getIss() {
            return iss;
        }

        @Override
        public String toString() {
            String s = super.toString();
            if (sub != null) {
                s = s.substring(0, s.length() - 1) + ", sub=" + getSub() + '}';
            }
            if (iss != null) {
                s = s.substring(0, s.length() - 1) + ", iss=" + getIss() + '}';
            }
            return s;
        }

        public void fromMap(Map<String, Object> map) {
            if (map == null) {
                return;
            }
            super.fromMap(map);
            if (map.get("sub") instanceof String) {
                sub = (String) map.get("sub");
            }
            if (map.get("iss") instanceof String) {
                iss = (String) map.get("iss");
            }
        }
    }

    public static void main(String... args) throws Exception {
        JWTTokenVerifier jwt = new JWTTokenVerifier();

        Token t = jwt.createToken(null, null, "a", "admin,dba", null);
        System.out.println("Created token : " + (System.identityHashCode(t) + "  ") + t);
        String ts = t.toText();
        System.out.println("JWT token     : " + ts);
        System.out.println("Status        : " + jwt.verify(ts));
        Token t2 = jwt.toToken(ts);
        System.out.println("Restored token: " + (System.identityHashCode(t2) + "  ") + t2);
        Token t3 = jwt.get(ts);
        System.out.println("Fetched token : " + (System.identityHashCode(t3) + "  ") + t3);
        System.out.println("created " + (t == t2 ? "==" : "!=") + " restored" + ", equals = " + t.equals(t2));
        System.out.println("created " + (t == t3 ? "==" : "!=") + " fetched" + " , equals = " + t.equals(t3));
        System.out.println("Map #1        : " + t.toMap());
        System.out.println("Map #2        : " + t2.toMap());
        System.out.println("Map #3        : " + t3.toMap());
    }

}
