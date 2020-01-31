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

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import ssg.lib.http.HttpAuthenticator.HttpSimpleAuth;
import ssg.lib.ssl.SSLTools;

/**
 *
 * @author 000ssg
 */
public class HttpAuthenticatorTest {

    public HttpAuthenticatorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of authenticate method, of class HttpAuthenticator.
     */
    @Test
    public void testAuthenticate_4args() throws IOException {
        System.out.println("authenticate_name_pwd");
        Object provider = null;
        String domain = "";
        String name = "aaa";
        String password = "aaapwd";
        HttpAuthenticator instance = new HttpAuthenticatorImpl();
        System.out.println("Reference instance of Http users:\n  " + instance.toString().replace("\n", "\n  "));
        HttpUser result = instance.authenticate(provider, domain, name, password);
        assertNotNull(result);
        assertEquals(name, result.id);
    }

    /**
     * Test of authenticate method, of class HttpAuthenticator.
     */
    @Test
    public void testAuthenticate_GenericType_Certificate() throws Exception {
        System.out.println("authenticate_cert");
        Object provider = "provider";

        List<String> aliases = Collections.list(ks.aliases());
        System.out.println("  certificate aliases: " + aliases);

        for (String[] kk : new String[][]{
            {"key_a", "aaa"},
            {"key_b", "bbb"},
            {"key_c", "ccc"},
            {"key_e", null}
        }) {
            String key = kk[0];
            String name = kk[1];
            Certificate[] certs = km.getCertificateChain(key);
            Certificate cert = (certs != null) ? certs[0] : null;
            if (cert == null) {
                assertNull(name);
            } else {
                HttpAuthenticator instance = new HttpAuthenticatorImpl();
                HttpUser result = instance.authenticate(provider, cert);
                assertNotNull(result);
                assertEquals(name, result.id);
            }
        }
    }

    /**
     * Test of getUser method, of class HttpAuthenticator.
     */
    @Test
    public void testGetUser() throws IOException {
        System.out.println("getUser");
        Object provider = "provider";
        HttpAuthenticator instance = new HttpAuthenticatorImpl();
        HttpUser expResult = instance.authenticate(provider, null, "aaa", "aaapwd");
        HttpUser result = instance.getUser(provider);
        assertEquals(expResult, result);
    }

    /**
     * Test of invalidate method, of class HttpAuthenticator.
     */
    @Test
    public void testInvalidate() throws IOException {
        System.out.println("invalidate");
        Object provider = "provider";
        HttpAuthenticator instance = new HttpAuthenticatorImpl();
        HttpUser expResult = instance.authenticate(provider, null, "aaa", "aaapwd");
        HttpUser result = instance.getUser(provider);
        assertEquals(expResult, result);
        instance.invalidate(provider);
        assertNull(instance.getUser(provider));
    }

    public static SSLContext sslCtx = SSLTools.tryCreateSSLContext(
            HttpAuthenticator.class.getClassLoader().getResource("ks/localhost__abc.p12"),
            "passw0rd",
            "passw0rd",
            HttpAuthenticator.class.getClassLoader().getResource("ks/localhost__abc_ts.p12"),
            "passw0rd",
            "TLS",
            false);

    public static KeyManagerFactory kmf = null;
    public static X509KeyManager km = null;
    public static KeyStore ks = null;

    static {
        try {
            ks = SSLTools.createKeyStore(
                    HttpAuthenticatorTest.class.getClassLoader().getResource("ks/localhost__abc.p12"),
                    "passw0rd"
            );
            kmf = SSLTools.createKeyManagerFactory(
                    ks,
                    "passw0rd"
            );
            km = (X509KeyManager) kmf.getKeyManagers()[0];
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public class HttpAuthenticatorImpl extends HttpSimpleAuth<Object> {

        public HttpAuthenticatorImpl() {
            try {
                load(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("users.txt")));
                for (Domain dom : domains.values()) {
                    dom.resolveCertificates(km);
                }
            } catch (IOException ioex) {
                ioex.printStackTrace();
            }
        }
    }

}
