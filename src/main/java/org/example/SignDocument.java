package org.example;

import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Enumeration;

public class SignDocument {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Must provide PKCS#11 conf file");
            System.exit(1);
        }

        // Load the PKCS#11 provider configuration
        Provider p = Security.getProvider("SunPKCS11");
        p = p.configure(args[0]);
        Security.addProvider(p);

        // Get an instance of the KeyStore
        KeyStore ks = KeyStore.getInstance("PKCS11");
        ks.load(null, "5678".toCharArray());

        // Get the private key and certificate
        Enumeration<String> aliases = ks.aliases();
        String alias = null;
        if (aliases.hasMoreElements()) {
            alias = aliases.nextElement();
        }

        if (alias == null) {
            System.out.println("No alias found in the key store.");
            return;
        }

        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, "5678".toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

        // Data to be signed
        byte[] data = Files.readAllBytes(Path.of("document.txt"));

        // Sign the data
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        byte[] signedData = signature.sign();

        // Write the signed data to a file
        try (FileOutputStream fos = new FileOutputStream("signature.txt")) {
            fos.write(signedData);
        }

        System.out.println("Document signed successfully.");
    }
}

