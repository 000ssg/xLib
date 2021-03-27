/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.http.dp.tokens;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JWT generator/verifier.
 *
 * Use JWT to generate/store authenticated users. To embed it into HTTP
 * application expose token create/verify functionality (JWTHttpDataProcessor)
 * and embed into HTTP authentication JWTUserVerifier. JWTUserVerifier may work
 * with JWT directly or remotely (via mentioned http data processor).
 *
 * @author 000ssg
 */
public abstract class TokenVerifier {

    public static enum TokenState {
        valid,
        expired,
        corrupted
    }

    // defaults
    long exp = 1000 * 60 * 15; // default expiration = 15 min
    // persistence
    TokenStore store = new TokenStoreRAM(this);

    public TokenVerifier() {
    }

    public String createTokenId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns type of token supported by verifier. May be use to separate
     * verifiers.
     *
     * @return
     */
    public abstract String type();

    /**
     * Return true if token can be verified by this verifier (e.g. based on
     * token structure).
     *
     * @param id
     * @return
     */
    public abstract boolean canVerify(String id);

    public TokenVerifier configureStore(TokenStore store) {
        if (store != null) {
            this.store = store;
            store.configure(this);
        }
        return this;
    }

    public <T extends Token> T create() {
        return (T) new Token(this);
    }

    public String toText(Token token) {
        return token != null ? token.getToken_id() : null;
    }

    public <T extends Token> T get(String id) {
        if (store != null) {
            return (T) store.get(id);
        }
        return null;
    }

    public TokenState verify(String id) {
        Token t = get(id);
        if (t == null) {
            return TokenState.corrupted;
        }
        long exp = System.currentTimeMillis() / 1000 - t.getExp();
        return exp < 0 ? TokenState.valid : TokenState.expired;
    }

    public Token createToken(String id, String domain, String name, String roles, Long expires, Object... extra) {
        Token token = create();
        token.id = id;
        token.domain = domain;
        token.name = name;
        token.roles = roles;
        if (expires != null) {
            token.exp = expires;
        }
        updateToken(token, extra);
        if (store != null) {
            store.add(token);
        }
        return token;
    }

    public void updateToken(Token token, Object... extra) {

    }

    public void takeOwnership(Token t) {
        t.tv = this;
    }

    public List<Token> find(String token_id, String domain, String id, String name) {
        return store.find(token_id, domain, id, name);
    }

    public static class Token {

        private TokenVerifier tv;
        private String token_id;
        private String domain;
        private String id;
        private String name;
        private String roles;
        private long exp;

        public Token() {
        }

        public Token(TokenVerifier tv) {
            this.tv = tv;
            token_id = tv.createTokenId();
            if (tv != null) {
                this.exp = (System.currentTimeMillis() + tv.exp) / 1000;
            }
        }

        public String toText() {
            return tv != null ? tv.toText(this) : null;
        }

        public TokenState verify() {
            return System.currentTimeMillis() / 1000 - getExp() < 0 ? TokenState.valid : TokenState.expired;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.getToken_id());
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
            if (this.getExp() != other.getExp()) {
                return false;
            }
            if (!Objects.equals(this.token_id, other.token_id)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return (getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()) + "{"
                    + "id=" + getToken_id()
                    + (domain != null ? ", domain=" + getDomain() : "")
                    + (name != null ? ", name=" + getName() : "")
                    + (roles != null ? ", roles=" + getRoles() : "")
                    + ", exp=" + getExp() + '}';
        }

        public Map<String, Object> toMap() {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Method m : getClass().getMethods()) {
                int mm = m.getModifiers();
                if (Modifier.isStatic(mm) || !Modifier.isPublic(mm) || m.getParameterCount() > 0) {
                    continue;
                }
                String n = m.getName();
                if (n.startsWith("get") && n.length() > 3) {
                    if ("getClass".equals(n)) {
                        continue;
                    }
                    n = Character.toLowerCase(n.charAt(3)) + (n.length() > 4 ? n.substring(4) : "");
                    try {
                        Object v = m.invoke(this);
                        if (v != null) {
                            r.put(n, v);
                        }
                    } catch (Throwable th) {
                    }
                } else if (n.startsWith("is") && n.length() > 2 && m.getReturnType() == Boolean.class || m.getReturnType() == boolean.class) {
                    n = Character.toLowerCase(n.charAt(2)) + (n.length() > 3 ? n.substring(3) : "");
                    try {
                        Object v = m.invoke(this);
                        if (v != null) {
                            r.put(n, v);
                        }
                    } catch (Throwable th) {
                    }
                }
            }
            return r;
        }

        public void fromMap(Map<String, Object> map) {
            if (map == null) {
                return;
            }
            if (map.get("token_id") instanceof String) {
                token_id = (String) map.get("token_id");
            }
            if (map.get("domain") instanceof String) {
                domain = (String) map.get("domain");
            }
            if (map.get("id") instanceof String) {
                id = (String) map.get("id");
            }
            if (map.get("name") instanceof String) {
                name = (String) map.get("name");
            }
            if (map.get("roles") instanceof String) {
                roles = (String) map.get("roles");
            }
            if (map.get("exp") instanceof Number) {
                exp = ((Number) map.get("exp")).longValue();
            }
        }

        /**
         * @return the token_id
         */
        public String getToken_id() {
            return token_id;
        }

        /**
         * @return the domain
         */
        public String getDomain() {
            return domain;
        }

        /**
         * @return the id
         */
        public String getId() {
            return id;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @return the roles
         */
        public String getRoles() {
            return roles;
        }

        /**
         * @return the exp
         */
        public long getExp() {
            return exp;
        }
    }

    public static interface TokenStore<T extends Token> {

        /**
         * Save token
         *
         * @param token
         * @return
         */
        T add(T token);

        /**
         * Fetch token
         *
         * @param tv
         * @return
         */
        T get(String tv);

        /**
         * Returns all tokens for given domain/id/name. If parameter null --
         * ignored.
         *
         * @param domain
         * @param id
         * @param name
         * @return
         */
        List<T> find(String token_id, String domain, String id, String name);

        /**
         * Callback - configure token conversion/analysis tool
         *
         * @param <Z>
         * @param tv
         * @return
         */
        <Z extends TokenStore<T>> Z configure(TokenVerifier tv);
    }

    public static class TokenStoreRAM<T extends Token> implements TokenStore<T> {

        Map<String, T> tokens = new LinkedHashMap<>();

        TokenVerifier tv;

        public TokenStoreRAM() {

        }

        public TokenStoreRAM(TokenVerifier tv) {
            this.tv = tv;
        }

        @Override
        public <Z extends TokenStore<T>> Z configure(TokenVerifier tv) {
            this.tv = tv;
            return (Z) this;
        }

        @Override
        public T add(T token) {
            tokens.put(tv.toText(token), token);
            return token;
        }

        @Override
        public T get(String id) {
            return tokens.get(id);
        }

        @Override
        public List<T> find(String token_id, String domain, String id, String name) {
            List<T> r = new ArrayList<>();
            Token[] tt = null;
            synchronized (tokens) {
                tt = tokens.values().toArray(new Token[tokens.size()]);
            }
            for (Token t : tt) {
                boolean ok = true;
                if (token_id != null && !t.getToken_id().equals(token_id)) {
                    ok = false;
                }
                if (ok && domain != null && !t.getDomain().equals(domain)) {
                    ok = false;
                }
                if (ok && id != null && !t.getId().equals(id)) {
                    ok = false;
                }
                if (ok && name != null && !t.getName().equals(name)) {
                    ok = false;
                }
                if (ok) {
                    r.add((T) t);
                }
            }
            return r;
        }
    }

    public static void main(String... args) throws Exception {
        TokenVerifier tv = new TokenVerifier() {
            @Override
            public boolean canVerify(String id) {
                return id != null;
            }

            @Override
            public String type() {
                return "test";
            }
        };

        Token t = tv.createToken("a_id", "def_domain", "a", "admin,dba", null);
        System.out.println("Created token : " + (System.identityHashCode(t) + "  ") + t);
        String ts = t.toText();
        System.out.println("Token         : " + ts);
        System.out.println("Status        : " + tv.verify(ts));
        System.out.println("Map           : " + t.toMap());
        Token t2 = tv.get(ts);
        System.out.println("Fetched token : " + (System.identityHashCode(t2) + "  ") + t2);
        System.out.println("created " + (t == t2 ? "==" : "!=") + " restored" + ", equals = " + t.equals(t2));
        System.out.println("Map #1        : " + t.toMap());
        System.out.println("Map #2        : " + t2.toMap());
    }
}
