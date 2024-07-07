package org.example;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;

public class PKCS11HttpsClient
{
    public static void main(String[] args)
    throws Exception {
        if (args.length == 0) {
            System.err.println("Must provide PKCS#11 conf file");
            System.exit(1);
        }

        Provider pkcs11Provider = Security.getProvider("SunPKCS11");
        pkcs11Provider = pkcs11Provider.configure(args[0]);
        Security.addProvider(pkcs11Provider);

        KeyStore ks = KeyStore.getInstance("PKCS11", pkcs11Provider);
        ks.load(null, "1234".toCharArray());

        // Set the SSL context to use the PKCS#11-based keystore and truststore
        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, null);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        SSLContext.setDefault(sslContext);

        URL url = new URL("https://localhost:8443/hello");
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sslContext.getSocketFactory());

        int responseCode = conn.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        try (
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(conn.getInputStream()))
        ) {
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            System.out.println("Response Content: " + content);
        }
    }
}
