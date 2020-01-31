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

import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import static ssg.lib.ssl.PKL.load;

/**
 *
 * @author 000ssg
 */
public class TestPKL {

    public static void main(String[] args) throws Exception {
        for (String uri : new String[]{"js/js.crt", "js/js.all", "js/js.pk"}) {
            Object obj = load(PKL.class.getClassLoader().getResource(uri), null);
            if (obj instanceof KeyPair) {
                KeyPair kp = (KeyPair) obj;
                System.out.println(uri + "\tPK: " + kp);
            }
            if (obj instanceof Certificate[]) {
                Certificate[] certs = (Certificate[]) obj;
                if (certs != null && certs.length > 0) {
                    System.out.println(uri + "\tCERT: " + certs[0].getType() + "  " + ((X509Certificate) certs[0]).getIssuerDN());
                }
            }

//            Map<String, String> r = read(PKL.class.getClassLoader().getResource(uri));
//
//            if (r.containsKey(PK_TYPE)) {
//                byte[] pkbin = Base64.getDecoder().decode(r.get(PK_BASE64));
//                KeyPair kp = loadPK(pkbin, null);
//                if (kp != null) {
//                    System.out.println(uri + "\tPK: " + kp);
//                }
//            }
//            if (r.containsKey(CERT_BASE64)) {
//                byte[] certbin = Base64.getDecoder().decode(r.get(CERT_BASE64));
//                Certificate[] certs = loadCert(certbin);
//                if (certs != null && certs.length > 0) {
//                    System.out.println(uri + "\tCERT: " + certs[0].getType() + "  " + ((X509Certificate) certs[0]).getIssuerDN());
//                }
//            }
        }
    }
}
