# PKCS#11 example

Minimal example of using PKCS#11 from Java without a real smart card.

We'll create two key pairs in the PKCS#11 key store, which could used for
anything. Since it's a pretty common scenario, we'll create one key pair for TLS
authentication and one to create digital signatures.

# Prerequisites

* Java 17+
* Maven 3

These instructions have been tested on MacOS using an M1 Mac Book Pro.

# Setup

This example uses [SoftHSM v2](https://github.com/opendnssec/SoftHSMv2) to create
a virtual PKCS#11-enabled smart card, and `keytool` to interact with it (i.e.,
to import private keys and certificates into it).

```bash
$ brew install softhsm
```

This is the SoftHSM configuration file, for reference:

```bash
$ cat /opt/homebrew/Cellar/softhsm/2.6.1/.bottle/etc/softhsm/softhsm2.conf
# SoftHSM v2 configuration file

directories.tokendir = /opt/homebrew/var/lib/softhsm/tokens/
objectstore.backend = file

# ERROR, WARNING, INFO, DEBUG
log.level = ERROR

# If CKF_REMOVABLE_DEVICE flag should be set
slots.removable = false

# Enable and disable PKCS#11 mechanisms using slots.mechanisms.
slots.mechanisms = ALL

# If the library should reset the state on fork
library.reset_on_fork = false
```

# Build

```bash
$ mvn clean install
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.753 s
[INFO] Finished at: 2024-06-14T12:35:24+02:00
[INFO] ------------------------------------------------------------------------
```

# Using the SoftHSM as a Java KeyStore

Initialize the token:

```bash
softhsm2-util --init-token --slot 0 --label "myToken" --pin 1234 --so-pin 5678
```

Create a PEM signing key pair and convert it into PKCS#12 format:

```bash
openssl genpkey -algorithm RSA -out signing-key.pem -pkeyopt rsa_keygen_bits:2048
openssl req -new -x509 -key signing-key.pem -out signing-cert.pem -days 365 -subj "/CN=signing"
openssl pkcs12 -export -in signing-cert.pem -inkey signing-key.pem -out signing.p12 -name signing -password pass:changeit
```

Create a PEM TLS authentication key pair and convert it into PKCS#12 format:

```bash
openssl genpkey -algorithm RSA -out tls-key.pem -pkeyopt rsa_keygen_bits:2048
openssl req -new -x509 -key tls-key.pem -out tls-cert.pem -days 365 -subj "/CN=tls"
openssl pkcs12 -export -in tls-cert.pem -inkey tls-key.pem -out tls.p12 -name tls -password pass:changeit
```

Import them into SoftHSM:

```bash
keytool -importkeystore -srckeystore signing.p12 -srcstoretype PKCS12 -srcstorepass changeit -destkeystore NONE -deststoretype PKCS11 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg pkcs11.cfg -alias signing -deststorepass 1234
keytool -importkeystore -srckeystore tls.p12 -srcstoretype PKCS12 -srcstorepass changeit -destkeystore NONE -deststoretype PKCS11 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg pkcs11.cfg -alias tls -deststorepass 1234
```

Verify:

```bash
keytool -list -keystore NONE -storetype PKCS11 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg pkcs11.cfg -storepass 1234
```

To show the token:

```bash
softhsm2-util --show-slots
```

To delete the token:

```bash
softhsm2-util --delete-token --token myToken
```

To use different slots, add this line to `pkcs11.cfg`:

```conf
slotListIndex = 1
```

By default, PKCS#11 uses slot 0.

# Showing key store contents

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.LoadCertificates pkcs11.cfg
Alias found: tls
Private Key Algorithm: RSA
Alias found: signing
Private Key Algorithm: RSA
```

# Signing a document

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.SignDocument pkcs11.cfg signing
Using alias: signing
Document signed successfully.
```

# Using the PKCS#11 keystore to connect to TLS with client authentication

To show this use case, download https://github.com/miladhub/jetty-tls-client-auth
into `../jetty-tls-client-auth`, build the project, create its keystore (but not
its truststore), and import its server certificate in the PKCS#11 truststore:

```bash
keytool -importkeystore -srckeystore ../jetty-tls-client-auth/keystore.p12 -srcstoretype PKCS12 -srcstorepass changeit -destkeystore NONE -deststoretype PKCS11 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg pkcs11.cfg -alias jetty -deststorepass 1234
```

Now use the client TLS authentication certificate as the server truststore:

```bash
cp tls.p12 ../jetty-tls-client-auth/truststore.p12
```

Start the server:

```bash
$ cd ../jetty-tls-client-auth/
$ mvn jetty:run
```

Test the HTTPS connection:

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.PKCS11HttpsClient pkcs11.cfg
Response Code: 200
Response Content: <h1>Hello Servlet</h1>session=node0i5mbom5hzrq912654pxkkalwy0
```

Deleting the 'tls' alias, the connection fails:

```bash
$ keytool -delete -alias tls -keystore NONE -storetype PKCS11 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg pkcs11.cfg -storepass 1234
...
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.PKCS11HttpsClient pkcs11.cfg
Exception in thread "main" javax.net.ssl.SSLHandshakeException: Received fatal alert: bad_certificate
...
```