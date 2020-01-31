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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 *
 * @author ssg
 */
public class PKL {

    // common
    public static final String FORMAT = "format";
    public static final String COMMENT = "comment";
    public static final String ERROR = "error";
    // certificate
    public static final String CERT_BASE64 = "certBase64";
    // PK - private key
    public static final String PK_TYPE = "pkType"; // for RSA/DSA
    public static final String PK_SPEC = "pkSpec"; // for OpenSSH's Proc-Type, PuTTY's
    public static final String PK_ENCRYPTED = "pkEncrypted"; // for indicating PK is encrypted
    public static final String PK_ENC_ALG = "pkEncAlg"; // for PK encryption algorythm
    public static final String PK_ENC_IV = "pkEncIV"; // for PK encryption initialization (IV value)
    public static final String PK_BASE64 = "pkBase64"; // for PK body encoded using Base64
    public static final String PK_MAC = "pkMAC"; // for PK body MAC
    // Public key
    public static final String PUB_TYPE = "pubType"; // for RSA/DSA
    public static final String PUB_SPEC = "pubSpec"; // for SSH2/SSH
    public static final String PUB_BASE64 = "pubBase64"; // for Public Key body encoded using Base64
    public static final String PUB_MAC = "pkMAC"; // for Public Key body MAC

    public static Map<String, String> read(URL resource) throws IOException {
        Map<String, String> result = null;
        Throwable err = null;
        try {
            result = PKL.readCertificate(resource.openStream());
        } catch (Throwable th) {
            err = th;
        }
        if (result == null || !result.containsKey(FORMAT)) {
            try {
                result = PKL.readOpenSSHPK(resource.openStream());
            } catch (Throwable th) {
                err = th;
            }
        }
        if (result == null || !result.containsKey(FORMAT)) {
            try {
                result = PKL.readPuttyPK(resource.openStream());
            } catch (Throwable th) {
                err = th;
            }
        }
        if (result == null || !result.containsKey(FORMAT)) {
            try {
                result = PKL.readSSHPK(resource.openStream());
            } catch (Throwable th) {
                err = th;
            }
        }
        if (result == null || !result.containsKey(FORMAT)) {
            try {
                result = PKL.readSSHPublicKey(resource.openStream());
            } catch (Throwable th) {
                err = th;
            }
        }
        if (result == null) {
            result = new LinkedHashMap<String, String>();
        }
        if (err != null) {
            result.put(ERROR, "" + err);
        }
        return result;
    }

    public static Map<String, String> readCertificate(InputStream source) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(source, "ISO-8859-1"));
        String s = null;
        boolean inCert = false;
        Boolean privateLines = null;
        StringBuilder sb = new StringBuilder();
        while ((s = lnr.readLine()) != null) {
            if (s.startsWith("--") && s.contains("CERTIFICATE")) {
                if (s.startsWith("-----BEGIN ")) {
                    inCert = true;
                    continue;
                } else if (s.contains("--END")) {
                    inCert = false;
                    result.put(CERT_BASE64, sb.toString());
                    //privateB = DatatypeConverter.parseBase64Binary(privateS);
                    privateLines = null;
                    sb.delete(0, sb.length());
                    continue;
                } else {
                    break;
                }
            }
            if (inCert) {
                if (s.isEmpty()) {
                } else {
                    sb.append(s);
                }
            }
        }
        lnr.close();
        if (!result.isEmpty()) {
            if (result.size() > 0) {
                result.put(FORMAT, "Certificate");
            }
        }
        return result;
    }

    public static Map<String, String> readOpenSSHPK(InputStream source) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(source, "ISO-8859-1"));
        String s = null;
        boolean inPK = false;
        Boolean privateLines = null;
        StringBuilder sb = new StringBuilder();
        while ((s = lnr.readLine()) != null) {
            if (s.startsWith("--") && s.contains("PRIVATE KEY")) {
                if (s.startsWith("-----BEGIN ")) {
                    inPK = true;
                    if (s.contains(" RSA ")) {
                        result.put(PK_TYPE, "RSA");
                    } else if (s.contains(" DSA ")) {
                        result.put(PK_TYPE, "DSA");
                    } else {
                        result.put(PK_TYPE, "UNKNOWN: " + s);
                    }
                    continue;
                } else if (s.startsWith("-----END ")) {
                    inPK = false;
                    result.put(PK_BASE64, sb.toString());
                    //privateB = DatatypeConverter.parseBase64Binary(privateS);
                    privateLines = null;
                    sb.delete(0, sb.length());
                    continue;
                } else {
                    break;
                }
            }
            if (inPK) {
                if (!s.contains(": ") && privateLines == null) {
                    privateLines = true;
                }
                if (s.contains(": ")) {
                    String n = s.substring(0, s.indexOf(':')).trim();
                    String v = s.substring(s.indexOf(':') + 1).trim();
                    if (n.startsWith("Proc-Type")) {
                        String type = v;
                        if (type.contains(",")) {
                            result.put(PK_SPEC, v.substring(0, v.indexOf(',')).trim());
                            if ("ENCRYPTED".equals(v.substring(v.indexOf(',') + 1).trim())) {
                                result.put(PK_ENCRYPTED, "true");
                            }
                        } else {
                            result.put(PK_SPEC, v);
                        }
                    } else if (n.equals("DEK-Info")) {
                        String encryption = v;
                        if (encryption.contains(",")) {
                            result.put(PK_ENC_ALG, v.substring(0, v.indexOf(',')).trim());
                            result.put(PK_ENC_IV, v.substring(v.indexOf(',') + 1).trim());
                            //privateMAC = DatatypeConverter.parseHexBinary(privateMACS);
                        } else {
                            result.put(PK_ENC_ALG, v);
                        }
                    }
                } else if (s.isEmpty()) {
                    privateLines = true;
                } else if (privateLines != null) {
                    sb.append(s);
                } else {
                    // ignore?
                }
            }
        }
        lnr.close();
        if (!result.isEmpty()) {
            if (result.size() > 1) {
                result.put(FORMAT, "OpenSSH");
            }
        }
        return result;
    }

    public static Map<String, String> readPuttyPK(InputStream source) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(source, "ISO-8859-1"));
        int keyLines = 0;
        String s = null;
        Boolean privateLines = null;
        StringBuilder sb = new StringBuilder();
        while ((s = lnr.readLine()) != null) {
            if (s.contains(": ")) {
                String n = s.substring(0, s.indexOf(':')).trim();
                String v = s.substring(s.indexOf(':') + 1).trim();
                if (n.startsWith("PuTTY-User")) {
                    result.put(PK_TYPE, v);
                    result.put(PK_TYPE + "-PuTTY", v);
                } else if (n.equals("Encryption")) {
                    result.put(PK_ENC_ALG, v);
                } else if (n.equals("Comment")) {
                    result.put(COMMENT, v);
                } else if (n.contains("-Lines")) {
                    privateLines = n.startsWith("Private-");
                    keyLines = Integer.parseInt(v);
                    if (result.containsKey(PK_TYPE + "-PuTTY")) {
                        String type = result.get(PK_TYPE + "-PuTTY").toLowerCase();
                        if (type.contains("rsa")) {
                            type = "RSA";
                        } else if (type.contains("dsa") || type.contains("dss")) {
                            type = "DSA";
                        }
                        if (privateLines) {
                            result.put(PK_TYPE, type);
                        } else {
                            result.put(PUB_TYPE, type);
                        }
                    }
                } else if (n.contains("-MAC")) {
                    if (n.startsWith("Public-")) {
                        result.put(PUB_MAC, v);
                        //publicMAC = DatatypeConverter.parseHexBinary(publicMACS);
                    } else if (n.startsWith("Private-")) {
                        result.put(PK_MAC, v);
                        //privateMAC = DatatypeConverter.parseHexBinary(privateMACS);
                    }
                }
            } else if (privateLines != null) {
                keyLines--;
                sb.append(s);
                if (keyLines == 0) {
                    if (privateLines) {
                        result.put(PK_BASE64, sb.toString());
                        //privateB = DatatypeConverter.parseBase64Binary(privateS);
                    } else {
                        result.put(PUB_BASE64, sb.toString());
                        //publicB = DatatypeConverter.parseBase64Binary(publicS);
                    }
                    sb.delete(0, sb.length());
                    privateLines = null;
                }
            } else {
                // ignore?
            }
        }
        lnr.close();
        if (!result.isEmpty()) {
            if (result.size() > 1) {
                result.put(FORMAT, "PuTTY");
            }
        }
        return result;
    }

    public static Map<String, String> readSSHPK(InputStream source) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(source, "ISO-8859-1"));
        String s = null;
        Boolean privateLines = null;
        StringBuilder sb = new StringBuilder();
        boolean inPK = false;
        while ((s = lnr.readLine()) != null) {
            if (s.startsWith("--") && s.contains("PRIVATE KEY")) {
                if (s.startsWith("---- BEGIN ")) {
                    inPK = true;
                    if (s.contains(" SSH2 ")) {
                        result.put(PK_SPEC, "SSH2");
                    } else if (s.contains(" SSH ")) {
                        result.put(PK_SPEC, "SSH");
                    } else {
                        result.put(PK_SPEC, "UNKNOWN: " + s);
                    }
                    continue;
                } else if (s.startsWith("---- END ")) {
                    inPK = false;
                    result.put(PK_BASE64, sb.toString());
                    privateLines = null;
                    sb.delete(0, sb.length());
                    continue;
                } else {
                    break;
                }
            }
            if (inPK) {
                if (s.contains(": ")) {
                    String n = s.substring(0, s.indexOf(':')).trim();
                    String v = s.substring(s.indexOf(':') + 1).trim();
                    if (n.startsWith("Comment")) {
                        result.put(COMMENT, v);
                    }
                    privateLines = true;
                } else if (privateLines != null) {
                    sb.append(s);
                } else {
                    // ignore?
                }
            }
        }
        lnr.close();
        if (!result.isEmpty()) {
            if (result.size() > 1) {
                result.put(FORMAT, "ssh.com");
            }
        }
        return result;
    }

    public static Map<String, String> readSSHPublicKey(InputStream source) throws IOException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(source, "ISO-8859-1"));
        String s = null;
        Boolean privateLines = null;
        StringBuilder sb = new StringBuilder();
        boolean inPK = false;
        while ((s = lnr.readLine()) != null) {
            if (s.startsWith("--") && s.contains("PUBLIC KEY")) {
                if (s.startsWith("---- BEGIN ")) {
                    inPK = true;
                    if (s.contains(" SSH2 ")) {
                        result.put(PUB_SPEC, "SSH2");
                    } else if (s.contains(" SSH ")) {
                        result.put(PUB_SPEC, "SSH");
                    } else {
                        result.put(PUB_SPEC, "UNKNOWN: " + s);
                    }
                    continue;
                } else if (s.startsWith("---- END ")) {
                    inPK = false;
                    result.put(PUB_BASE64, sb.toString());
                    privateLines = null;
                    sb.delete(0, sb.length());
                    continue;
                } else {
                    break;
                }
            }
            if (inPK) {
                if (s.contains(": ")) {
                    String n = s.substring(0, s.indexOf(':')).trim();
                    String v = s.substring(s.indexOf(':') + 1).trim();
                    if (n.startsWith("Comment")) {
                        result.put(COMMENT, v);
                    }
                    privateLines = true;
                } else if (privateLines != null) {
                    sb.append(s);
                } else {
                    // ignore?
                }
            }
        }
        lnr.close();
        if (!result.isEmpty()) {
            if (result.size() > 1) {
                result.put(FORMAT, "ssh.com");
            }
        }
        return result;
    }

    private static byte[] normalizePassword(String password) {
        byte[] result = new byte[password.length()];
        int idx = 0;
        for (char ch : password.toCharArray()) {
            result[idx++] = (byte) ch;
        }
        return result;
    }

    private static byte[] generateOpenSSHDerivedKey(byte[] password, byte[] iv, int bytesNeeded) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("md5");
        } catch (Throwable th) {
        }
        byte[] buf = new byte[digest.getDigestLength()];
        byte[] key = new byte[bytesNeeded];
        int offset = 0;

        for (;;) {
            digest.update(password, 0, password.length);
            digest.update(iv, 0, iv.length);

            buf = digest.digest();

            int len = (bytesNeeded > buf.length) ? buf.length : bytesNeeded;
            System.arraycopy(buf, 0, key, offset, len);
            offset += len;

            // check if we need any more
            bytesNeeded -= len;
            if (bytesNeeded == 0) {
                break;
            }

            // do another round
            digest.reset();
            digest.update(buf, 0, buf.length);
        }

        return key;
    }

    /**
     * Create ("load") public/private key pair its binary serialized form.
     *
     * @param privKeyBytes
     * @param password
     * @return
     */
    public static KeyPair loadPK(byte[] privKeyBytes, String password) {
        if (password == null) {
            try {
                // Now derive the RSA public key from the private key
                KeyFactory rsaKeyFac = KeyFactory.getInstance("RSA");
                KeySpec ks = new PKCS8EncodedKeySpec(privKeyBytes);
                RSAPrivateKey privKey = (RSAPrivateKey) rsaKeyFac.generatePrivate(ks);
                RSAPrivateKey rsaPriv = (RSAPrivateKey) privKey;
                RSAPublicKeySpec rsaPubKeySpec = new RSAPublicKeySpec(rsaPriv.getModulus(), rsaPriv.getPrivateExponent());
                RSAPublicKey rsaPubKey = (RSAPublicKey) rsaKeyFac.generatePublic(rsaPubKeySpec);
                return new KeyPair(rsaPubKey, privKey);
            } catch (Throwable th) {
                th.printStackTrace();
//            } catch (NoSuchAlgorithmException nsaex) {
//            } catch (NoSuchPaddingException nsaex) {
//            } catch (InvalidKeySpecException nsaex) {
//            } catch (InvalidKeyException nsaex) {
//            } catch (InvalidAlgorithmParameterException nsaex) {
//            } catch (IOException ioex) {
            }
        } else {
            try {
                EncryptedPrivateKeyInfo encryptPKInfo = new EncryptedPrivateKeyInfo(privKeyBytes);
                Cipher cipher = Cipher.getInstance(encryptPKInfo.getAlgName());
                PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray());
                SecretKeyFactory secFac = SecretKeyFactory.getInstance(encryptPKInfo.getAlgName());
                Key pbeKey = secFac.generateSecret(pbeKeySpec);
                AlgorithmParameters algParams = encryptPKInfo.getAlgParameters();
                cipher.init(Cipher.DECRYPT_MODE, pbeKey, algParams);
                KeySpec pkcs8KeySpec = encryptPKInfo.getKeySpec(cipher);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey privKey = kf.generatePrivate(pkcs8KeySpec);

                KeyFactory rsaKeyFac = KeyFactory.getInstance("RSA");
                // Now derive the RSA public key from the private key
                RSAPrivateKey rsaPriv = (RSAPrivateKey) privKey;
                RSAPublicKeySpec rsaPubKeySpec = new RSAPublicKeySpec(rsaPriv.getModulus(), rsaPriv.getPrivateExponent());
                RSAPublicKey rsaPubKey = (RSAPublicKey) rsaKeyFac.generatePublic(rsaPubKeySpec);
                return new KeyPair(rsaPubKey, privKey);
            } catch (Throwable th) {
                th.printStackTrace();
//            } catch (NoSuchAlgorithmException nsaex) {
//            } catch (NoSuchPaddingException nsaex) {
//            } catch (InvalidKeySpecException nsaex) {
//            } catch (InvalidKeyException nsaex) {
//            } catch (InvalidAlgorithmParameterException nsaex) {
//            } catch (IOException ioex) {
            }
        }
        return null;
    }

    public static Certificate[] loadCert(byte[] certBytes) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));
            return new Certificate[]{cert};
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static <T> T load(URL resource, String password) throws IOException {
        if (resource != null) {
            Map<String, String> r = read(resource);
            if (r.containsKey(PK_TYPE)) {
                byte[] pkbin = Base64.getDecoder().decode(r.get(PK_BASE64));
                KeyPair kp = loadPK(pkbin, password);
                if (kp != null) {
                    return (T) kp;
                }
            }
            if (r.containsKey(CERT_BASE64)) {
                byte[] certbin = Base64.getDecoder().decode(r.get(CERT_BASE64));
                Certificate[] certs = loadCert(certbin);
                if (certs != null && certs.length > 0) {
                    return (T) certs;
                }
            }
        }
        return null;
    }
}
