/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.http.dp.jwt;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import ssg.lib.common.JSON;
import ssg.lib.common.Refl;

/**
 * JWT generator/verifier
 *
 * @author sesidoro
 */
public class JWT {

    public static enum JWTState {
        valid,
        expired,
        corrupted
    }
    // JSON support
    static Refl refl = new Refl.ReflJSON();
    static JSON.Encoder encoder = new JSON.Encoder(refl);
    static JSON.Decoder decoder = new JSON.Decoder(refl);

    // signer support
    SecureRandom secureRandom;
    KeyPair keyPair;
    // header
    String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    // defaults
    String sub = "test";
    String iss = "self";
    long exp = 1000 * 60 * 15; // default expiration = 15 min
    // persistence
    JWTStore store = new JWTStoreRAM(this);

    public JWT() {
        try {
            secureRandom = new SecureRandom();
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException nsaex) {
            nsaex.printStackTrace();
        }
    }

    public JWT configureStore(JWTStore store) {
        if (store != null) {
            this.store = store;
        }
        return this;
    }

    public <T extends Token> T create() {
        return (T) new Token(this);
    }

    public String toJWT(Token token) {
        StringBuilder sb = new StringBuilder();
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate(), secureRandom);

            String head1B64 = Base64.getEncoder().encodeToString(header.getBytes("UTF-8"));
            String payload1B64 = Base64.getEncoder().encodeToString(encoder.writeObject(token).getBytes("UTF-8"));
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

    public <T extends Token> T get(String jwtToken) {
        if (store != null) {
            return (T) store.get(jwtToken);
        }
        return toToken(jwtToken);
    }

    public JWTState verify(String jwtToken) {
        Token t = get(jwtToken);
        if (t == null) {
            return JWTState.corrupted;
        }
        long exp = System.currentTimeMillis() / 1000 - t.exp;
        return exp < 0 ? JWTState.valid : JWTState.expired;
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
                t = decoder.readObject(body, t.getClass());
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

    public Token findJWT(String id, String name) {
        Token t = store.find(id);
        if (t == null) {
            t = store.find(name);
        }
        return t;
    }

    public Token createJWT(String id, String name, String roles, Long expires) {
        Token token = create();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        token.id = id;
        token.name = name;
        token.roles = roles;
        token.sub = this.sub;
        token.iss = this.iss;
        if (expires != null) {
            token.exp = expires;
        }
        if (store != null) {
            store.add(token);
        }
        return token;
    }

    public static class Token {

        private JWT jwt;
        public String id;
        public String sub;
        public String name;
        public String roles;
        public String iss;
        public long exp;

        public Token() {
        }

        public Token(JWT jwt) {
            this.jwt = jwt;
            if (jwt != null) {
                this.exp = (System.currentTimeMillis() + jwt.exp) / 1000;
            }
        }

        public String toJWT() {
            return jwt != null ? jwt.toJWT(this) : null;
        }

        public JWTState verify() {
            return System.currentTimeMillis() / 1000 - exp < 0 ? JWTState.valid : JWTState.expired;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.id);
            hash = 79 * hash + Objects.hashCode(this.sub);
            hash = 79 * hash + Objects.hashCode(this.name);
            hash = 79 * hash + Objects.hashCode(this.roles);
            hash = 79 * hash + Objects.hashCode(this.iss);
            hash = 79 * hash + (int) (this.exp ^ (this.exp >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Token other = (Token) obj;
            if (this.exp != other.exp) {
                return false;
            }
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            if (!Objects.equals(this.sub, other.sub)) {
                return false;
            }
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.roles, other.roles)) {
                return false;
            }
            if (!Objects.equals(this.iss, other.iss)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return (getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()) + "{" + "id=" + id + ", sub=" + sub + ", name=" + name + ", roles=" + roles + ", iss=" + iss + ", exp=" + exp + '}';
        }
    }

    public static interface JWTStore<T extends Token> {

        T add(T token);

        T get(String jwt);

        T find(String id);
    }

    public static class JWTStoreRAM<T extends Token> implements JWTStore<T> {

        Map<String, T> tokens = new LinkedHashMap<>();

        JWT jwt;

        public JWTStoreRAM(JWT jwt) {
            this.jwt = jwt;
        }

        @Override
        public T add(T token) {
            tokens.put(token.id, token);
            return token;
        }

        @Override
        public T get(String jwt) {
            T t = this.jwt.toToken(jwt);
            if (t != null && t.id != null) {
                return tokens.get(t.id);
            }
            return null;
        }

        @Override
        public T find(String id) {
            return id != null ? tokens.get(id) : null;
        }
    }

    public static void main(String... args) throws Exception {
        JWT jwt = new JWT();

        Token t = jwt.createJWT(null, "a", "admin,dba", null);
        System.out.println("Created token : " + (System.identityHashCode(t) + "  ") + t);
        String ts = t.toJWT();
        System.out.println("JWT token     : " + ts);
        System.out.println("Status        : " + jwt.verify(ts));
        Token t2 = jwt.toToken(ts);
        System.out.println("Restored token: " + (System.identityHashCode(t2) + "  ") + t2);
        Token t3 = jwt.get(ts);
        System.out.println("Fetched token : " + (System.identityHashCode(t3) + "  ") + t3);
        System.out.println("created " + (t == t2 ? "==" : "!=") + " restored" + ", equals = " + t.equals(t2));
        System.out.println("created " + (t == t3 ? "==" : "!=") + " fetched" + " , equals = " + t.equals(t3));
    }
}
