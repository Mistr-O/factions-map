// Based on the libadb-android test-app example (Apache-2.0 or GPL-3.0-or-later).
package com.example.vradbterminal;

import android.content.Context;
import android.os.Build;
import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public final class AdbConnectionManager extends AbsAdbConnectionManager {
    private static volatile AdbConnectionManager instance;
    private final PrivateKey privateKey;
    private final Certificate certificate;

    public static synchronized AdbConnectionManager getInstance(Context context) throws Exception {
        if (instance == null) instance = new AdbConnectionManager(context.getApplicationContext());
        return instance;
    }

    private AdbConnectionManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        PrivateKey savedKey = readPrivateKey(context);
        Certificate savedCert = readCertificate(context);
        if (savedKey == null || savedCert == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair pair = generator.generateKeyPair();
            savedKey = pair.getPrivate();
            PublicKey publicKey = pair.getPublic();

            String algorithm = "SHA512withRSA";
            Date from = new Date();
            Date until = new Date(System.currentTimeMillis() + 315360000000L); // 10 years
            CertificateExtensions extensions = new CertificateExtensions();
            extensions.set("SubjectKeyIdentifier", new SubjectKeyIdentifierExtension(
                    new KeyIdentifier(publicKey).getIdentifier()));
            X500Name name = new X500Name("CN=VR ADB Terminal");
            extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(from, until));
            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
            info.set("subject", new CertificateSubjectName(name));
            info.set("key", new CertificateX509Key(publicKey));
            info.set("validity", new CertificateValidity(from, until));
            info.set("issuer", new CertificateIssuerName(name));
            info.set("extensions", extensions);
            X509CertImpl cert = new X509CertImpl(info);
            cert.sign(savedKey, algorithm);
            savedCert = cert;
            writePrivateKey(context, savedKey);
            writeCertificate(context, savedCert);
        }
        privateKey = savedKey;
        certificate = savedCert;
    }

    @Override protected PrivateKey getPrivateKey() { return privateKey; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "VR-ADB-Terminal"; }

    private static Certificate readCertificate(Context context) {
        File file = new File(context.getFilesDir(), "adb-cert.pem");
        if (!file.exists()) return null;
        try (InputStream input = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        } catch (Exception ignored) { return null; }
    }

    private static PrivateKey readPrivateKey(Context context) {
        File file = new File(context.getFilesDir(), "adb-private.key");
        if (!file.exists()) return null;
        try (InputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0, count;
            while (offset < bytes.length && (count = input.read(bytes, offset, bytes.length - offset)) > 0) offset += count;
            EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (IOException | InvalidKeySpecException | java.security.NoSuchAlgorithmException ignored) {
            return null;
        }
    }

    private static void writePrivateKey(Context context, PrivateKey key) throws IOException {
        try (OutputStream output = new FileOutputStream(new File(context.getFilesDir(), "adb-private.key"))) {
            output.write(key.getEncoded());
        }
    }

    private static void writeCertificate(Context context, Certificate certificate)
            throws IOException, CertificateEncodingException {
        try (OutputStream output = new FileOutputStream(new File(context.getFilesDir(), "adb-cert.pem"))) {
            output.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            output.write('\n');
            new BASE64Encoder().encode(certificate.getEncoded(), output);
            output.write('\n');
            output.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }
    }
}