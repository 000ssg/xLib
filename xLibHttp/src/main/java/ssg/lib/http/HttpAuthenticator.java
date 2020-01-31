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
package ssg.lib.http;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import ssg.lib.http.HttpAuthenticator.UserCertificateVerifier.CertificateKey;
import ssg.lib.http.HttpAuthenticator.UserPasswordVerifier.PasswordVerifier;
import ssg.lib.http.base.HttpRequest;

/**
 *
 * @author 000ssg
 */
public interface HttpAuthenticator<P> {

    HttpUser authenticate(P provider, String domain, String name, String password) throws IOException;

    HttpUser authenticate(P provider, HttpRequest request) throws IOException;

    HttpUser authenticate(P provider, Certificate cert) throws IOException;

    HttpUser authenticate(P provider, Object... parameters) throws IOException;

    HttpUser getUser(P provider);

    void invalidate(P provider);

    public static class HttpSimpleAuth<P> implements HttpAuthenticator<P> {

        Map<String, Domain> domains = new LinkedHashMap<>();
        Domain defaultDomain;

        Map<P, HttpUser> users = new LinkedHashMap<>(); // provider -> user

        public HttpSimpleAuth() {
        }

        public HttpSimpleAuth(File file) throws IOException {
            load(new FileReader(file));
        }

        public HttpSimpleAuth(URL url) throws IOException {
            load(new InputStreamReader(url.openStream()));
        }

        public HttpSimpleAuth(InputStream is) throws IOException {
            load(new InputStreamReader(is));
        }

        public void addDomain(Domain domain) {
            if (domain != null && domain.name != null) {
                if (domains.isEmpty()) {
                    defaultDomain = domain;
                }
                domains.put(domain.name, domain);
            }
        }

        public void removeDomain(String name) {
            if (domains.containsKey(name) && domains.size() > 1) {
                Domain dom = domains.remove(name);
                if (defaultDomain == dom) {
                    defaultDomain = domains.values().iterator().next();
                }
            }
        }

        @Override
        public HttpUser authenticate(P provider, String domain, String name, String password) throws IOException {
            Domain<P> dom = (domain == null || !domains.containsKey(domain)) ? defaultDomain : domains.get(domain);

            if (name != null) {
                HttpUser user = dom.authenticate(provider, domain, name, password);
                if (user != null) {
                    users.put(provider, user);
                    return user;
                }
            }
            return null;
        }

        @Override
        public HttpUser authenticate(P provider, HttpRequest request) {
            if (request != null && request.getHttpSession() != null && request.getHttpSession().getApplication() != null) {
                String domain = request.getHttpSession().getApplication().getRoot();
                Domain<P> dom = (domain == null || !domains.containsKey(domain)) ? defaultDomain : domains.get(domain);
                return (dom != null) ? dom.authenticate(provider, request) : null;
            }
            return null;
        }

        @Override
        public HttpUser authenticate(P provider, Certificate cert) throws IOException {
            if (cert != null && cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;

                for (Domain<P> dom : domains.values()) {
                    HttpUser user = dom.authenticate(provider, cert);
                    if (user != null) {
                        users.put(provider, user);
                        return user;
                    }
                }
            }
            return null;
        }

        @Override
        public HttpUser authenticate(P provider, Object... parameters) throws IOException {
            for (Domain<P> dom : domains.values()) {
                HttpUser user = dom.authenticate(provider, parameters);
                if (user != null) {
                    users.put(provider, user);
                    return user;
                }
            }
            return null;
        }

        @Override
        public HttpUser getUser(P provider) {
            return users.get(provider);
        }

        @Override
        public void invalidate(P provider) {
            if (users.containsKey(provider)) {
                HttpUser user = users.remove(provider);
                Domain dom = domains.get(user.domain);
                if (dom != null) {
                    dom.invalidate(provider);
                }
            }
        }

        public void clear() {
            users.clear();
            if (defaultDomain != null) {
                defaultDomain.clear();
            }
            for (Domain dom : domains.values()) {
                dom.clear();
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName()));
            sb.append("{"
                    + "users=" + users.size()
                    + ", domains=" + domains.size());
            if (!users.isEmpty()) {
                sb.append("\n  users:");
                for (P p : users.keySet()) {
                    sb.append("\n    " + p + " -> " + users.get(p).user);
                }
            }
            sb.append("\n  domains:");
            for (String p : domains.keySet()) {
                sb.append("\n    " + p + " -> " + domains.get(p).toString().replace("\n", "\n    "));
            }
            sb.append('}');
            return sb.toString();
        }

        public void load(Reader reader) throws IOException {
            LineNumberReader lnr = (reader instanceof LineNumberReader) ? (LineNumberReader) reader : new LineNumberReader(reader);
            String s = null;
            String domainName = "default";
            Domain dom = null;
            while ((s = lnr.readLine()) != null) {
                s = s.trim();
                if (s.startsWith("#") || s.isEmpty()) {
                    continue;
                }
                if (s.startsWith("@")) {
                    domainName = s.substring(1).trim();
                    continue;
                }

                if (dom == null) {
                    if (domains.containsKey(domainName)) {
                        dom = domains.get(domainName);
                    } else {
                        dom = new Domain();
                        dom.name = domainName;
                        if (domains.isEmpty()) {
                            defaultDomain = dom;
                        }
                        if (domains.isEmpty()) {
                            defaultDomain = dom;
                        }
                        domains.put(dom.name, dom);
                    }
                }

                int idx = s.indexOf("=");
                if (idx == -1) {
                    continue;
                }
                String name = s.substring(0, idx);
                String pwd = null;
                String cert = null;
                RAT rat = new RAT();
                String[] ss = s.substring(idx + 1).trim().split("\\|");
                idx = ss[0].indexOf("/");
                if (idx == -1) {
                    pwd = ss[0];
                } else {
                    pwd = ss[0].substring(0, idx);
                    cert = ss[0].substring(idx + 1);
                }
                if (ss.length > 1) {
                    String[] sss = ss[1].split(",");
                    if (sss.length > 0 && !sss[0].trim().isEmpty()) {
                        if (rat.getRoles() == null) {
                            rat.setRoles(new ArrayList<String>());
                        }
                        for (String s0 : sss) {
                            s0 = s0.trim();
                            if (!rat.getRoles().contains(s0)) {
                                rat.getRoles().add(s0);
                            }
                        }
                    }
                }
                if (ss.length > 2) {
                    String[] sss = ss[2].split(",");
                    if (sss.length > 0 && !sss[0].trim().isEmpty()) {
                        if (rat.getActions() == null) {
                            rat.setActions(new ArrayList<String>());
                        }
                        for (String s0 : sss) {
                            s0 = s0.trim();
                            if (!rat.getActions().contains(s0)) {
                                rat.getActions().add(s0);
                            }
                        }
                    }
                }
                if (ss.length > 3) {
                    String[] sss = ss[3].split(",");
                    if (sss.length > 0 && !sss[0].trim().isEmpty()) {
                        if (rat.getTags() == null) {
                            rat.setTags(new ArrayList<String>());
                        }
                        for (String s0 : sss) {
                            s0 = s0.trim();
                            if (!rat.getTags().contains(s0)) {
                                rat.getTags().add(s0);
                            }
                        }
                    }
                }

                dom.addUser(name, pwd, cert, rat);
//                dom.userNames.put(name, pwd);
//                if (cert != null) {
//                    dom.userCerts.put(cert, name);
//                }
//                dom.userAccess.put(name, rat);
            }
        }

        public void save(Writer writer) throws IOException {
            writer.write("#@domain_name\n");
            writer.write("#<user name>=<pwd>[/<cert alias or DN>]|<role>[,<role>]*[|<action>[,<action>]*[|<tag>[,<tag>]*]]\n");

            for (Domain<P> dom : domains.values()) {
                Collection<UserVerifier> vs = dom.store.verifiers();
                UserPasswordVerifier pv = null;
                UserCertificateVerifier cv = null;
                for (UserVerifier v : vs) {
                    if (v instanceof UserPasswordVerifier && pv == null) {
                        pv = (UserPasswordVerifier) v;
                    } else if (v instanceof UserCertificateVerifier && cv == null) {
                        cv = (UserCertificateVerifier) v;
                    }
                }

                writer.write("\n@" + dom.name + "\n");
                for (String name : dom.store.names()) {
                    PasswordVerifier pwd = (pv != null) ? pv.userNames.get(name) : null; // dom.userNames.get(name);
                    String cert = null;
                    //if (dom.userCerts.values().contains(name)) {
                    if (cv != null && cv.userCerts.values().contains(name)) {
                        for (Entry<String, String> e : cv.userCerts.entrySet()) {
                            if (name.equals(e.getValue())) {
                                cert = e.getKey();
                                break;
                            }
                        }
                    }
                    RAT rat = dom.store.getRAT(name);
                    writer.write(name);
                    writer.write("=");
                    writer.write((pwd != null) ? pwd.value : "");
                    writer.write((cert != null) ? "/" + cert : "");
                    if (rat != null) {
                        for (List<String> ls : new List[]{
                            rat.getRoles(),
                            rat.getActions(),
                            rat.getTags()
                        }) {
                            writer.write("|");
                            boolean first = true;
                            if (ls != null) {
                                for (String s : ls) {
                                    if (first) {
                                        first = false;
                                    } else {
                                        writer.write(",");
                                    }
                                    writer.write(s);
                                }
                            }
                        }
                    }
                    writer.write("\n");
                }
            }
        }
    }

    public static class Domain<P> implements HttpAuthenticator<P> {

        String name;
        UserStore store = new UserStoreSimple();
        Map<P, HttpUser> users = new LinkedHashMap<>(); // provider -> user

        public Domain() {
        }

        public Domain(String name) {
            this.name = name;
        }

        public Domain(String name, UserStore store) {
            this.name = name;
            this.store = store;
        }

        public UserStore getUserStore() {
            return store;
        }

        @Override
        public HttpUser authenticate(P provider, String domain, String name, String password) throws IOException {
            if (name != null && store.hasName(name)) {
                VerificationResult vr = store.verify(name, password);
                if (vr != null && name.equals(vr.userId)) {
                    HttpUser user = new HttpUser();
                    user.domain = (vr.userDomain != null) ? vr.userDomain : this.name;
                    user.id = vr.userId;
                    user.user = vr.userName;
                    user.rat = store.getRAT(name);
                    users.put(provider, user);
                    user.getProperties().put(HttpUser.P_AUTH_TYPE, HttpUser.AUTH_TYPE.password);
                    return user;
                }
            }
            return null;
        }

        @Override
        public HttpUser authenticate(P provider, HttpRequest request) {
            String ba = request.getHead().getHeader1("Authorization");
            if (ba != null && ba.startsWith("Basic ")) {
                try {
                    String[] up = new String(Base64.getDecoder().decode(ba.substring(ba.indexOf(" ") + 1)), "UTF-8").split(":");
                    HttpUser r = authenticate(provider, null, up[0], up[1]);
                    if (r != null) {
                        r.getProperties().put(HttpUser.P_AUTH_TYPE, HttpUser.AUTH_TYPE.basic);
                    }
                } catch (IOException ioex) {
                    ioex.printStackTrace();
                }
            }
            return null;
        }

        @Override
        public HttpUser authenticate(P provider, Certificate cert) throws IOException {
            if (cert != null) {
                VerificationResult vr = store.verify(cert);

                if (vr != null) {
                    HttpUser user = new HttpUser();
                    user.domain = (vr.userDomain != null) ? vr.userDomain : this.name;
                    user.id = vr.userId;
                    user.user = vr.userName;
                    user.rat = store.getRAT(vr.userId);
                    users.put(provider, user);
                    user.getProperties().put(HttpUser.P_AUTH_TYPE, HttpUser.AUTH_TYPE.certificate);
                    return user;
                }
            }
            return null;
        }

        @Override
        public HttpUser authenticate(P provider, Object... parameters) throws IOException {
            VerificationResult vr = store.getVerificationResult(parameters);

            if (vr != null && vr.userId != null) {
                HttpUser user = new HttpUser();
                user.domain = (vr.userDomain != null) ? vr.userDomain : this.name;
                user.id = vr.userId;
                user.user = vr.userName;
                user.rat = store.getRAT(vr.userId);
                users.put(provider, user);
                user.getProperties().put(HttpUser.P_AUTH_TYPE, vr.verifier.getType());
                Map<String, Object> vps = vr.verifier.verifiedProperties(parameters);
                if (vps != null) {
                    user.getProperties().putAll(vps);
                }
                return user;
            }
            return null;
        }

        public HttpUser toUser(HttpUser user, String id) {
            P provider = null;
            boolean found = false;
            for (Entry<P, HttpUser> entry : users.entrySet()) {
                if (entry.getValue().equals(user)) {
                    provider = entry.getKey();
                    found = true;
                    break;
                }
            }
            if (provider != null || found) {
                HttpUser u = new HttpUser();
                u.domain = name;
                if (user.domain != null && !user.domain.startsWith(name)) {
                    u.domain += ":" + user.domain;
                }
                u.id = id;
                u.user = user.user;
                u.rat = store.getRAT(id);
                u.getProperties().putAll(user.getProperties());
                users.remove(provider);
                users.put(provider, u);
                return u;
            } else {
                return user;
            }
        }

        @Override
        public HttpUser getUser(P provider) {
            return users.get(provider);
        }

        @Override
        public void invalidate(P provider) {
            if (users.containsKey(provider)) {
                users.remove(provider);
            }
        }

        public void clear() {
            users.clear();
        }

        public void resolveCertificates(KeyManager km) {
            store.resolveCertificates(km);
        }

        public boolean hasUser(String name) {
            return store.hasName(name);
        }

        public void addUser(String name, String pwd, String dn, RAT rat) {
            store.registerUser(name, (pwd != null) ? new PasswordVerifier(pwd) : null, dn != null ? new CertificateKey(dn) : null, rat);
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName()));
            sb.append("{"
                    + name
                    + "\n  store=" + store.toString().replace("\n", "\n  "));
            if (!users.isEmpty()) {
                sb.append("\n  Active users:");
                for (Entry<P, HttpUser> entry : users.entrySet()) {
                    sb.append("\n    " + entry.getValue().getName() + " (" + entry.getKey() + ")");
                }
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * User verifier.
     *
     * Use registerUSer to add user for parameters set.
     *
     * Use canVerify to ensure verify can be used for parameters and avoid
     * exception.
     *
     * Use verify to get user name for parameters.
     */
    public static interface UserVerifier extends Serializable, Cloneable {

        boolean canVerify(Object... parameters);

        VerificationResult verify(Object... parameters) throws IOException;

        Map<String, Object> verifiedProperties(Object... parameters);

        boolean registerUser(String name, Object... parameters);

        boolean unregisterUser(String name);

        HttpUser.AUTH_TYPE getType();
    }

    public static class VerificationResult {

        public UserVerifier verifier;
        public String userId;
        public String userName;
        public String userDomain;

        public VerificationResult() {
        }

        public VerificationResult(
                UserVerifier verifier,
                String userId,
                String userName,
                String userDomain
        ) {
            this.verifier = verifier;
            this.userId = userId;
            this.userName = userName;
            this.userDomain = userDomain;
        }
    }

    public static interface UserStore extends UserVerifier {

        Iterable<String> names();

        boolean hasName(String name);

        void resolveCertificates(KeyManager km);

        boolean setUserRAT(String name, RAT rat);

        RAT getRAT(String name);

        VerificationResult getVerificationResult(Object... parameters) throws IOException;

        Collection<UserVerifier> verifiers();
    }

    public static class UserPasswordVerifier implements UserVerifier {

        Map<String, PasswordVerifier> userNames = new LinkedHashMap<>(); // user id -> password

        @Override
        public boolean canVerify(Object... parameters) {
            return parameters != null
                    && parameters.length == 2
                    && parameters[0] instanceof String
                    && parameters[1] instanceof String;
        }

        @Override
        public VerificationResult verify(Object... parameters) throws IOException {
            if (canVerify(parameters)) {
                String name = (String) parameters[0];
                String password = (String) parameters[1];
                PasswordVerifier pwd = userNames.get(name);
                if (pwd != null && pwd.match(password)) {
                    return new VerificationResult(this, name, null, null);
                }
            }
            return null;
        }

        @Override
        public Map<String, Object> verifiedProperties(Object... parameters) {
            return null;
        }

        @Override
        public boolean registerUser(String name, Object... parameters) {
            if (name != null && parameters != null && parameters.length > 0 && parameters[0] instanceof PasswordVerifier) {
                userNames.put(name, (PasswordVerifier) parameters[0]);
                return true;
            }
            return false;
        }

        @Override
        public boolean unregisterUser(String name) {
            if (userNames.containsKey(name)) {
                userNames.remove(name);
                return true;
            }
            return false;
        }

        @Override
        public HttpUser.AUTH_TYPE getType() {
            return HttpUser.AUTH_TYPE.password;
        }

        public static class PasswordVerifier implements Serializable, Cloneable {

            String value;

            public PasswordVerifier() {
            }

            public PasswordVerifier(String value) {
                this.value = value;
            }

            boolean match(String pwd) {
                return value.equals(pwd);
            }
        }
    }

    public static class UserCertificateVerifier implements UserVerifier {

        Map<String, String> userCerts = new LinkedHashMap<>(); // dn - user name
        transient Map<String, String> alias2key = new LinkedHashMap<>(); // alias -> dn
        transient Map<String, String> key2alias = new LinkedHashMap<>(); // dn -> alias

        /**
         * Check if has supported certificates in the parameters list
         *
         * @param parameters
         * @return
         */
        @Override
        public boolean canVerify(Object... parameters) {
            Certificate[] certs = getCertificates(parameters);
            return certs != null && certs.length > 0;
        }

        @Override
        public VerificationResult verify(Object... parameters) throws IOException {
            Certificate[] certs = getCertificates(parameters);
            for (Certificate cert : certs) {
                CertificateKey dn = getCertificateKey(cert);
                String a = key2alias.get(dn.value);

                String name = userCerts.get(dn.value);
                if (name == null && a != null) {
                    name = userCerts.get(a);
                }
                if (name != null) {
                    return new VerificationResult(this, name, null, null);
                }
            }
            return null;
        }

        @Override
        public Map<String, Object> verifiedProperties(Object... parameters) {
            return null;
        }

        /**
         * If parameter has Certificate or Certificate[] the 1st supported with
         * evaluable key is used.
         *
         * Otherwise 1st string parameter is treated as key and registered for
         * the user.
         *
         * @param name
         * @param parameters
         * @return
         */
        @Override
        public boolean registerUser(String name, Object... parameters) {
            if (name == null || parameters == null || parameters.length < 1) {
                return false;
            }

            Certificate[] certs = getCertificates(parameters);
            if (certs != null && certs.length > 0) {
                for (Certificate cert : certs) {
                    CertificateKey key = getCertificateKey(cert);
                    if (key != null) {
                        userCerts.put(key.value, name);
                        return true;
                    }
                }
            }

            for (Object p : parameters) {
                if (p instanceof CertificateKey) {
                    CertificateKey key = (CertificateKey) p;
                    userCerts.put(key.value, name);
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean unregisterUser(String name) {
            if (name != null) {
                if (userCerts.containsValue(name)) {
                    Collection<String> keys = new HashSet<String>();
                    for (Entry<String, String> entry : userCerts.entrySet()) {
                        if (name.equals(entry.getValue())) {
                            keys.add(entry.getKey());
                        }
                    }
                    if (!keys.isEmpty()) {
                        for (String key : keys) {
                            userCerts.remove(key);
                            String a = (key2alias.containsKey(key)) ? key2alias.get(key) : null;
                            if (a != null && alias2key.containsKey(a)) {
                                alias2key.remove(a);
                            }
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public HttpUser.AUTH_TYPE getType() {
            return HttpUser.AUTH_TYPE.certificate;
        }

        /**
         * Scan parameters for suppported certificates.
         *
         * @param parameters
         * @return
         */
        public Certificate[] getCertificates(Object... parameters) {
            Certificate[] certs = new Certificate[0];
            if (parameters != null) {
                for (Object p : parameters) {
                    if (p instanceof Certificate && isSupportedCertificate((Certificate) p)) {
                        Certificate cert = (Certificate) p;
                        certs = Arrays.copyOf(certs, certs.length + 1);
                        certs[certs.length - 1] = cert;
                    } else if (p instanceof Certificate[]) {
                        Certificate[] cc = (Certificate[]) p;
                        for (Certificate c : cc) {
                            if (isSupportedCertificate(c)) {
                                certs = Arrays.copyOf(certs, certs.length + 1);
                                certs[certs.length - 1] = c;
                            }
                        }
                    }
                }
            }
            return certs;
        }

        public boolean isSupportedCertificate(Certificate cert) {
            return cert != null && cert instanceof X509Certificate;
        }

        public CertificateKey getCertificateKey(Certificate cert) {
            if (cert instanceof X509Certificate) {
                X509Certificate x509 = (X509Certificate) cert;

                String dn = x509.getIssuerDN().getName();
                return new CertificateKey(dn);
            }
            return null;
        }

        public void resolveCertificates(KeyManager km) {
            if (km instanceof X509KeyManager) {
                X509KeyManager x509km = (X509KeyManager) km;
                for (String a : userCerts.keySet()) {
                    X509Certificate[] x509s = x509km.getCertificateChain(a);
                    if (x509s != null && x509s.length > 0) {
                        String dn = x509s[0].getIssuerDN().getName();
                        key2alias.put(dn, a);
                        alias2key.put(a, dn);
                    }
                }
            }
        }

        public static class CertificateKey implements Serializable, Cloneable {

            String value;

            public CertificateKey() {
            }

            public CertificateKey(String value) {
                this.value = value;
            }
        }
    }

    public static class UserStoreSimple implements UserStore {

        Map<String, RAT> userAccess = new LinkedHashMap<>(); // user name -> RAT

        List<UserVerifier> verifiers = new ArrayList<UserVerifier>() {
            {
                add(new UserPasswordVerifier());
                add(new UserCertificateVerifier());
            }
        };

        public UserStoreSimple() {
        }

        @Override
        public Iterable<String> names() {
            return userAccess.keySet();
        }

        @Override
        public boolean hasName(String name) {
            return userAccess.containsKey(name);
        }

        @Override
        public boolean canVerify(Object... parameters) {
            for (UserVerifier v : verifiers) {
                if (v.canVerify(parameters)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public VerificationResult verify(Object... parameters) throws IOException {
            VerificationResult r = null;
            for (UserVerifier v : verifiers) {
                if (v.canVerify(parameters)) {
                    r = v.verify(parameters);
                    if (r != null) {
                        break;
                    }
                }
            }
            return r;
        }

        @Override
        public Map<String, Object> verifiedProperties(Object... parameters) {
            for (UserVerifier v : verifiers) {
                if (v.canVerify(parameters)) {
                    try {
                        VerificationResult r = v.verify(parameters);
                        if (r != null) {
                            return v.verifiedProperties(parameters);
                        }
                    } catch (Throwable th) {
                        break;
                    }
                }
            }
            return null;
        }

        @Override
        public boolean registerUser(String name, Object... parameters) {
            boolean added = false;
            if (name != null) {
                // find RAT, if any
                RAT rat = null;
                if (parameters != null) {
                    for (Object p : parameters) {
                        if (p instanceof RAT) {
                            rat = (RAT) p;
                            break;
                        }
                    }
                }
                // register in any/all provided verifiers
                for (UserVerifier v : verifiers) {
                    if (v.registerUser(name, parameters)) {
                        added = true;
                    }
                }
                // register if at least 1 verifier registered user successfully
                if (added && !userAccess.containsKey(name)) {
                    userAccess.put(name, rat);
                }
            }
            return added;
        }

        @Override
        public boolean unregisterUser(String name) {
            boolean removed = false;
            if (name != null) {
                if (userAccess.containsKey(name)) {
                    userAccess.remove(name);
                    removed = true;
                }
                // register in any/all provided verifiers
                for (UserVerifier v : verifiers) {
                    if (v.unregisterUser(name)) {
                        removed = true;
                    }
                }
            }
            return removed;
        }

        @Override
        public HttpUser.AUTH_TYPE getType() {
            return HttpUser.AUTH_TYPE.none;
        }

        @Override
        public RAT getRAT(String name) {
            return userAccess.get(name);
        }

        public VerificationResult getVerificationResult(Object... parameters) throws IOException {
            VerificationResult r = null;

            if (canVerify(parameters)) {
                for (UserVerifier v : verifiers) {
                    if (v.canVerify(parameters)) {
                        r = v.verify(parameters);
                        if (r != null) {
                            break;
                        }
                    }
                }
            }

            return r;
        }

        @Override
        public Collection<UserVerifier> verifiers() {
            return verifiers;
        }

        public void resolveCertificates(KeyManager km) {
            for (UserVerifier v : verifiers) {
                if (v instanceof UserCertificateVerifier) {
                    ((UserCertificateVerifier) v).resolveCertificates(km);
                }
            }
        }

        @Override
        public boolean setUserRAT(String name, RAT rat) {
            if (name != null) {
                userAccess.put(name, rat);
                return true;
            }
            return false;
        }

        public void clear() {
            userAccess.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName()));
            sb.append("{");
            if (!userAccess.isEmpty()) {
                sb.append("\n  access:");
                for (String n : userAccess.keySet()) {
                    sb.append("\n    " + n + " -> " + ("" + userAccess.get(n)).replace("\n", "\n    "));
                }
            }
            if (!verifiers.isEmpty()) {
                sb.append("\n  verifiers:");
                for (UserVerifier v : verifiers) {
                    sb.append("\n    " + ("" + v).replace("\n", "\n    "));
                }
            }
            sb.append('}');
            return sb.toString();
        }

    }

}
