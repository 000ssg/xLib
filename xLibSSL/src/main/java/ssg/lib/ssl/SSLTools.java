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
package ssg.lib.ssl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 *
 * @author sesidoro
 */
public class SSLTools {

    public static SSLContext composeSSLContext(URL trustStoreURL, String trustStorePassword, String protocol, PrivateKeyCertificateInfo... pkcis) throws
            KeyStoreException,
            IOException,
            NoSuchAlgorithmException,
            CertificateException,
            UnrecoverableKeyException,
            KeyManagementException {

        KeyStore ks = createKeyStore(null, null);
        ks.load(null, null);

        KeyStore ts = createKeyStore(null, null);
        if (trustStoreURL != null) {
            ts.load(trustStoreURL.openStream(), (trustStorePassword != null) ? trustStorePassword.toCharArray() : null);
        } else {
            ts.load(null, null);
        }

        if (pkcis != null) {
            for (PrivateKeyCertificateInfo pkci : pkcis) {
                Certificate[] ca = PKL.load(pkci.ca, null);
                Certificate[] cert = PKL.load(pkci.cert, null);
                KeyPair kp = PKL.load(pkci.pk, null);
                if (cert != null) {
                    if (SSLTools.addCertificate(
                            ks,
                            ts,
                            (ca != null && ca.length > 0) ? ca[0] : null,
                            (cert != null && cert.length > 0) ? cert[0] : null,
                            kp,
                            pkci.alias,
                            pkci.password
                    )) {
                        System.out.println("Added   " + pkci);
                    } else {
                        System.out.println("Ignored " + pkci);
                    }
                }
            }
        }

        SSLHelper helper = new SSLHelper(
                ks,
                ts,
                ""
        );
        
        
        return helper.createSSLContext((protocol != null) ? protocol : "TLS", false);
    }

    /**
     * Creates context based on keystore/truststore info and given protocol.
     *
     * @param keyStoreURL
     * @param keyStorePassword
     * @param keyPassword
     * @param trustStoreURL
     * @param trustStorePassword
     * @param protocol
     * @return
     * @throws KeyStoreException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    public static SSLContext createSSLContext(
            URL keyStoreURL,
            String keyStorePassword,
            String keyPassword,
            URL trustStoreURL,
            String trustStorePassword,
            String protocol,
            boolean trustAll
    ) throws
            KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException,
            KeyManagementException {

        SSLHelper helper = new SSLHelper(
                keyStoreURL,
                keyStorePassword,
                keyPassword,
                trustStoreURL,
                trustStorePassword
        );
        return helper.createSSLContext(protocol, trustAll);
    }

    public static SSLContext tryCreateSSLContext(
            URL keyStoreURL,
            String keyStorePassword,
            String keyPassword,
            URL trustStoreURL,
            String trustStorePassword,
            String protocol,
            boolean trustAll
    ) {
        try {
            return SSLTools.createSSLContext(keyStoreURL, keyStorePassword, keyPassword, trustStoreURL, trustStorePassword, protocol, trustAll);
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    public static KeyManagerFactory createKeyManagerFactory(
            URL keyStoreURL,
            String keyStorePassword,
            String keyPassword
    ) throws
            KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException,
            KeyManagementException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(keyStoreURL.openStream(), keyStorePassword.toCharArray());
        return createKeyManagerFactory(ks, keyPassword);
    }

    public static KeyManagerFactory createKeyManagerFactory(
            KeyStore ks,
            String keyPassword
    ) throws
            KeyStoreException,
            IOException,
            NoSuchAlgorithmException,
            CertificateException,
            UnrecoverableKeyException,
            KeyManagementException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyPassword.toCharArray());
        return kmf;
    }

    public static KeyStore createKeyStore(
            URL keyStoreURL,
            String keyStorePassword
    ) throws
            KeyStoreException,
            IOException,
            NoSuchAlgorithmException,
            CertificateException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        if (keyStoreURL != null) {
            char[] ksPassphrase = keyStorePassword.toCharArray();

            ks.load(keyStoreURL.openStream(), ksPassphrase);
        }
        return ks;
    }

    public static SSLHelper createSSLHelper(
            URL keyStoreURL,
            String keyStorePassword,
            String keyPassword,
            URL trustStoreURL,
            String trustStorePassword
    ) throws
            KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException,
            KeyManagementException {
        return new SSLHelper(
                keyStoreURL,
                keyStorePassword,
                keyPassword,
                trustStoreURL,
                trustStorePassword
        );
    }

    public static SSLHelper createSSLHelper(
            KeyStore ks,
            KeyStore ts,
            String keyPassword
    ) throws
            KeyStoreException,
            NoSuchAlgorithmException,
            UnrecoverableKeyException {
        return new SSLHelper(
                ks,
                ts,
                keyPassword
        );
    }

    /**
     * SSL helper encapsulates key-related elements used to build SSL context:
     * the key store, management factory, trust manager factory.
     */
    public static class SSLHelper {

        private KeyStore ks;
        private KeyStore ts;
        private String keyPwd;

        public SSLHelper(
                final KeyStore ks,
                final KeyStore ts,
                String keyPassword
        ) throws
                KeyStoreException,
                NoSuchAlgorithmException,
                UnrecoverableKeyException {
            this.ks = ks;
            this.ts = ts;
            keyPwd = keyPassword;
        }

        public SSLHelper(
                URL keyStoreURL,
                String keyStorePassword,
                String keyPassword,
                URL trustStoreURL,
                String trustStorePassword
        ) throws
                KeyStoreException, IOException, NoSuchAlgorithmException,
                CertificateException, UnrecoverableKeyException,
                KeyManagementException {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ts = KeyStore.getInstance(KeyStore.getDefaultType());

            ks.load(keyStoreURL.openStream(), keyStorePassword.toCharArray());
            ts.load(trustStoreURL.openStream(), trustStorePassword.toCharArray());

            keyPwd = keyPassword;
        }

        /**
         * @return the ks
         */
        public KeyStore getKs() {
            return ks;
        }

        /**
         * @return the ts
         */
        public KeyStore getTs() {
            return ts;
        }

        public List<String> getKeyAliases() {
            try {
                return Collections.list(ks.aliases());
            } catch (KeyStoreException ksex) {
                return Collections.emptyList();
            }
        }

        public List<String> getTrustedAliases() {
            try {
                return Collections.list(ts.aliases());
            } catch (KeyStoreException ksex) {
                return Collections.emptyList();
            }
        }

        public void addTrustedCertificatesFrom(SSLHelper sslh, String... aliases) {
            if (sslh != null && aliases != null && aliases.length > 0) {
                KeyStore srcTs = sslh.getTs();
                KeyStore dstTs = getTs();
                copyCertificates(srcTs, dstTs, aliases);
            }
        }

        public void addTrustedCertificatesFrom(URL tsURL, String pwd, String... aliases) throws
                KeyStoreException,
                IOException,
                NoSuchAlgorithmException,
                CertificateException {
            if (tsURL != null) {
                KeyStore srcTs = KeyStore.getInstance(KeyStore.getDefaultType());
                srcTs.load(tsURL.openStream(), pwd.toCharArray());

                if (aliases == null || aliases.length == 0 || aliases[0] == null) {
                    List<String> aas = Collections.list(srcTs.aliases());
                    aliases = aas.toArray(new String[aas.size()]);
                }

                KeyStore dstTs = getTs();
                copyCertificates(srcTs, dstTs, aliases);
            }
        }

        public void addJavaTrustedCertificates() throws
                KeyStoreException,
                IOException,
                NoSuchAlgorithmException,
                CertificateException {
            String tsLocation = System.getProperty("javax.net.ssl.trustStore");
            String tsPassword = System.getProperty("javax.net.ssl.trustStorePassword");

            if (tsLocation == null) {
                String jh = System.getProperty("java.home").replace("\\", "/");
                tsLocation = jh + "/lib/security/cacerts";
            }
            if (tsPassword == null) {
                tsPassword = "changeit";
            }

            File file = new File(tsLocation);
            addTrustedCertificatesFrom(file.toURI().toURL(), tsPassword);
        }

        public SSLHelper limitTo(String... aliases) throws
                KeyStoreException,
                IOException,
                CertificateException,
                NoSuchAlgorithmException,
                UnrecoverableKeyException {

            KeyStore ks2 = duplicate(ks);
            KeyStore ts2 = duplicate(ts);

            // evaluate unique alias names
            Collection<String> aaa = new LinkedHashSet<>();
            for (String s : aliases) {
                aaa.add(s);
            }

            for (String a : this.getKeyAliases()) {
                if (!aaa.contains(a)) {
                    ks2.deleteEntry(a);
                }
            }

            return createSSLHelper(ks2, ts2, keyPwd);
        }

        public SSLHelper limitTo(Map<String, String> aliases) throws
                KeyStoreException,
                IOException,
                CertificateException,
                NoSuchAlgorithmException,
                UnrecoverableKeyException {

            KeyStore ks2 = duplicate(ks);
            KeyStore ts2 = duplicate(ts);

            Map<String, Certificate> ccs = new LinkedHashMap<>();
            Map<String, Certificate> tcs = new LinkedHashMap<>();
            List<String> aas = getKeyAliases();

            for (String a : this.getKeyAliases()) {
                if (!aliases.containsKey(a)) {
                    ks2.deleteEntry(a);
                    ts2.deleteEntry(a);
                } else {
                    ccs.put(aliases.get(a), ks2.getCertificate(a));
                    tcs.put(aliases.get(a), ts2.getCertificate(a));
                    ks2.deleteEntry(a);
                    ts2.deleteEntry(a);
                }
            }

            for (String a : ccs.keySet()) {
                ks2.setCertificateEntry(a, ccs.get(a));
                ts2.setCertificateEntry(a, tcs.get(a));
            }

            return createSSLHelper(ks2, ts2, keyPwd);
        }

        public KeyManagerFactory createKmf() throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyPwd.toCharArray());
            return kmf;
        }

        public TrustManagerFactory createTmf() throws NoSuchAlgorithmException, KeyStoreException {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            return tmf;
        }

        public SSLContext createSSLContext(
                String protocol,
                boolean trustAll
        ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            KeyManagerFactory kmf = createKmf();
            TrustManagerFactory tmf = createTmf();
            SSLContext sslCtx = SSLContext.getInstance(protocol);
            sslCtx.init(
                    kmf.getKeyManagers(),
                    (trustAll) ? new TrustManager[]{new TrustAllTrustManager(ts)} : tmf.getTrustManagers(),
                    new SecureRandom());

            return sslCtx;
        }

        public SSLContext tryCreateSSLContext(
                String protocol,
                boolean trustAll
        ) {
            try {
                KeyManagerFactory kmf = createKmf();
                TrustManagerFactory tmf = createTmf();
                SSLContext sslCtx = SSLContext.getInstance(protocol);
                sslCtx.init(
                        kmf.getKeyManagers(),
                        (trustAll) ? new TrustManager[]{new TrustAllTrustManager(ts)} : tmf.getTrustManagers(),
                        new SecureRandom());
                return sslCtx;
            } catch (Throwable th) {
                th.printStackTrace();
                return null;
            }
        }

        @Override
        public String toString() {
            return "SSLHelper{"
                    + "\n  ks=" + toString(ks).replace("\n", "\n  ")
                    + "\n  ts=" + toString(ts).replace("\n", "\n  ")
                    + '\n'
                    + '}';
        }

        public String toString(KeyStore ks) {
            StringBuilder sb = new StringBuilder();

            try {
                List<String> aliases = Collections.list(ks.aliases());

                sb.append((getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()));

                sb.append(": " + ks.getType() + "[" + aliases.size() + "]: " + aliases);

                for (String alias : aliases) {
                    Certificate cert = ks.getCertificate(alias);
                    Certificate[] chain = ks.getCertificateChain(alias);
                    sb.append("\n  " + alias + "[" + ((chain != null) ? chain.length : "<no chain>") + "]: " + toString(cert).replace("\n", "\n  "));
                }

            } catch (KeyStoreException ksex) {
                sb.append("\n  error: " + ksex);
            } catch (Throwable th) {
                sb.append("\n  severe: " + th);
            }
            return sb.toString();
        }

        public String toString(Certificate... certs) {
            StringBuilder sb = new StringBuilder();
            for (Certificate cert : certs) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                if (cert instanceof X509Certificate) {
                    X509Certificate x509 = (X509Certificate) cert;
                    sb.append((x509.getClass().isAnonymousClass() ? x509.getClass().getName() : x509.getClass().getSimpleName()));
                    sb.append(": " + x509.getIssuerDN());
                } else {
                    sb.append(cert);
                }
            }
            return sb.toString();
        }

        public SSLHelper copy() {
            try {
                return SSLTools.createSSLHelper(duplicate(ks), duplicate(ts), keyPwd);
            } catch (Throwable th) {
                return null;
            }
        }
    }

    public static KeyStore duplicate(KeyStore ks) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ks.store(baos, "password".toCharArray());

            KeyStore ks2 = KeyStore.getInstance(ks.getType());
            ks2.load(new ByteArrayInputStream(baos.toByteArray()), "password".toCharArray());

            return ks2;
        } catch (Throwable th) {
            return null;
        }
    }

    public static List<String> copyCertificates(KeyStore a, KeyStore b, String... aliases) {
        List<String> r = new ArrayList<>();

        if (a != null && b != null && aliases != null) {
            for (String alias : aliases) {
                if (alias == null) {
                    continue;
                }
                try {
                    Certificate[] cs = a.getCertificateChain(alias);
                    if (cs == null) {
                        cs = new Certificate[]{a.getCertificate(alias)};
                    }
                    if (cs != null && cs.length > 0 && cs[0] != null) {
                        for (int i = cs.length - 1; i >= 0; i--) {
                            b.setCertificateEntry(alias, cs[i]);
                        }
                        r.add(alias);
                    }
                } catch (KeyStoreException ksex) {
                    ksex.printStackTrace();
                }
            }
        }

        return r;
    }

    public static boolean addCertificate(KeyStore ks, KeyStore ts, Certificate ca, Certificate cert, KeyPair kp, String alias, String password) {
        boolean r = false;
        if (ks != null && kp != null && cert != null && alias != null) {
            if (addPrivateKey(ks, alias, kp.getPrivate(), password, cert, ca)) {
                r = true;
            }
        }
        if (ts != null && cert != null && alias != null) {
            if (addCertificate(ts, alias, cert)) {
                r = true;
            }
        }

        return r;
    }

    public static boolean addPrivateKey(KeyStore ks, String alias, PrivateKey pk, String password, Certificate cert, Certificate ca) {
        boolean r = false;
        if (ks != null && alias != null && cert != null) {
            try {
                ks.setKeyEntry(
                        alias,
                        pk,
                        (password != null)
                                ? password.toCharArray()
                                : null,
                        (ca != null)
                                ? new Certificate[]{cert, ca}
                        : new Certificate[]{cert}
                );
                r = true;
            } catch (KeyStoreException ksex) {
                ksex.printStackTrace();
            }
        }
        return r;
    }

    public static boolean addCertificate(KeyStore ks, String alias, Certificate cert) {
        boolean r = false;
        if (ks != null && alias != null && cert != null) {
            try {
                ks.setCertificateEntry(alias, cert);
                r = true;
            } catch (KeyStoreException ksex) {
                ksex.printStackTrace();
            }
        }
        return r;
    }

    public static class TrustAllTrustManager extends X509ExtendedTrustManager {

        public static PrintStream logger;
        KeyStore ts;
        List<X509Certificate> certs;
        String prefix = "%%%%%%%%%%%%%%%%%%%%%%%%%% ";

        public TrustAllTrustManager(KeyStore ts) {
            this.ts = ts;
        }

        void log(String s, X509Certificate... cs) {
            if (logger == null) {
                return;
            }
            logger.println(prefix + s);
            if (cs != null) {
                for (X509Certificate c : cs) {
                    if (c != null) {
                        logger.println(prefix + "  " + c.getIssuerDN() + "; " + c.getType() + "; " + c.getSigAlgName());
                    }
                }
            }
        }

        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            log(Thread.currentThread().getName() + ": checkClientTrusted:" + authType + ":" + certs[0], certs);
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            log(Thread.currentThread().getName() + ": checkServerTrusted:" + authType + ":" + certs[0], certs);
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            if (certs == null) {
                certs = new ArrayList<>();
                try {
                    for (String a : Collections.list(ts.aliases())) {
                        Certificate c = ts.getCertificate(a);
                        if (c instanceof X509Certificate) {
                            certs.add((X509Certificate) c);
                        }
                    }
                } catch (Throwable th) {
                }
            }
            log(Thread.currentThread().getName() + ": getAcceptedIssuers: " + ((certs != null) ? certs.size() : "<no certs>"));
            return (certs == null) ? null : certs.toArray(new X509Certificate[certs.size()]);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] xcs, String string, Socket socket) throws CertificateException {
            log(Thread.currentThread().getName() + ": checkClientTrusted (socket):" + string + ":" + socket, xcs);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] xcs, String string, Socket socket) throws CertificateException {
            log(Thread.currentThread().getName() + ": checkServerTrusted (socket):" + string + ":" + socket, xcs);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {
            log(Thread.currentThread().getName() + ": checkClientTrusted (engine):" + string + ":" + ssle, xcs);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {
            log(Thread.currentThread().getName() + ": checkServerTrusted (engine):" + string + ":" + ssle, xcs);
        }

    }

    public static class PrivateKeyCertificateInfo {

        String alias;
        String password = "";
        URL ca;
        URL cert;
        URL pk;

        public PrivateKeyCertificateInfo(
                String alias,
                URL ca,
                URL cert,
                URL pk,
                String password
        ) {
            this.alias = alias;
            this.ca = ca;
            this.cert = cert;
            this.pk = pk;
            this.password = password;
        }

        public PrivateKeyCertificateInfo(
                String alias,
                URL ca,
                URL cert,
                URL pk
        ) {
            this.alias = alias;
            this.ca = ca;
            this.cert = cert;
            this.pk = pk;
        }

        public boolean checkPK() {
            return checkCert() && pk != null;
        }

        public boolean checkCert() {
            return alias != null && cert != null;
        }
    }
}
