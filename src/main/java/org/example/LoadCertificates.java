package org.example;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

public class LoadCertificates {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Must provide PKCS#11 conf file");
            System.exit(1);
        }

        try {
            // Load the PKCS#11 provider configuration
            Provider p = Security.getProvider("SunPKCS11");
            p = p.configure(args[0]);
            Security.addProvider(p);

            // Get an instance of the KeyStore
            KeyStore ks = KeyStore.getInstance("PKCS11");
            ks.load(null, "5678".toCharArray());

            // List aliases
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                System.out.println("Alias found: " + alias);

                // Check if it is a certificate
                if (ks.isCertificateEntry(alias)) {
                    X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
                    System.out.println("Certificate Subject: " + cert.getSubjectX500Principal());
                }

                // Check if it is a key entry
                if (ks.isKeyEntry(alias)) {
                    Key key = ks.getKey(alias, null);
                    if (key instanceof PrivateKey) {
                        PrivateKey privateKey = (PrivateKey) key;
                        System.out.println("Private Key Algorithm: " + privateKey.getAlgorithm());
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
