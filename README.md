# PKCS#11 example

Minimal example of using PKCS#11 from Java without a real smart card.

# Prerequisites

* Java 17+
* Maven 3

These instructions have been tested on MacOS using an M1 Mac Book Pro.

# Setup

This example uses [SoftHSM v2](https://github.com/opendnssec/SoftHSMv2) to
create a virtual PKCS#11-enabled smart card, and [OpenSC](https://github.com/OpenSC/OpenSC)
to interact with it (i.e., to import a private key and certificate into it).

```bash
$ brew install softhsm
$ brew install opensc
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

# Using a single slot

By default, PKCS#11 uses slot 0, and this line in `pkcs11.cfg` is optional:
```conf
slotListIndex = 0
```

First we will create a primary key and a certificate and import them in slot 0.

## Create a private key and certificate in PEM format

```bash
rm *.pem *.der
openssl genpkey -algorithm RSA -out private_key.pem
openssl req -new -key private_key.pem -x509 -days 365 -out certificate.pem \
  -subj "/C=IT/ST=IT/L=BO/O=ACME/OU=IT/CN=example.com"
```

## Convert them into DER format

```bash
openssl pkcs8 -topk8 -inform PEM -outform DER -in private_key.pem -out private_key.der -nocrypt
openssl x509 -in certificate.pem -outform DER -out certificate.der
```

## Import them into a SoftHSM token

```bash
$ softhsm2-util --init-token --slot 0 --label "MyToken" --so-pin 1234 --pin 5678
The token has been initialized and is reassigned to slot 889004786

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --write-object private_key.der --type privkey --id 01 --label "MyPrivateKey"
Using slot 0 with a present token (0x34fd22f2)
Created private key:
Private Key Object; RSA
  label:      MyPrivateKey
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --write-object certificate.der --type cert --id 01 --label "MyCertificate"
Using slot 0 with a present token (0x34fd22f2)
Created certificate:
Certificate Object; type = X.509 cert
  label:      MyCertificate
  subject:    DN: C=IT, ST=IT, L=BO, O=ACME, OU=IT, CN=example.com
  serial:     077B788A12B8D57AB44FE521AAB1FBF0AE0BE48E
  ID:         01
```

This verifies that the private key and certificate have been imported:

```bash
$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so --login --pin 5678 --list-objects

Using slot 0 with a present token (0x34fd22f2)
Private Key Object; RSA
  label:      MyPrivateKey
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive
Certificate Object; type = X.509 cert
  label:      MyCertificate
  subject:    DN: C=IT, ST=IT, L=BO, O=ACME, OU=IT, CN=example.com
  serial:     077B788A12B8D57AB44FE521AAB1FBF0AE0BE48E
  ID:         01
```

## Sign a document from Java

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.SignDocument pkcs11.cfg
Document signed successfully.
```

## Showing key store contents:

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.LoadCertificates pkcs11.cfg
Alias found: MyCertificate
Private Key Algorithm: RSA
```

# Mixing certificates in one slot

This is an example of using one PKCS#11 slot, mixing a primary key and a
certificate  that was not originated from that primary key, for example in the
case where the primary key is used for signing, and the certificate is used for
TLS authentication.

## Create two private key and two certificates in PEM format

```bash
rm *.pem *.der
openssl genpkey -algorithm RSA -out pk_signing.pem
openssl req -new -key pk_signing.pem -x509 -days 365 -out cert_signing.pem \
  -subj "/C=US/ST=State/L=City/O=Organization/OU=Department/CN=example.com"
openssl genpkey -algorithm RSA -out pk_tls.pem
openssl req -new -key pk_tls.pem -x509 -days 365 -out cert_tls.pem \
  -subj "/C=US/ST=State/L=City/O=Organization/OU=Department/CN=example.com"
```

## Convert them into DER format

```bash
openssl pkcs8 -topk8 -inform PEM -outform DER -in pk_signing.pem -out pk_signing.der -nocrypt
openssl x509 -in cert_signing.pem -outform DER -out cert_signing.der
openssl pkcs8 -topk8 -inform PEM -outform DER -in pk_tls.pem -out pk_tls.der -nocrypt
openssl x509 -in cert_tls.pem -outform DER -out cert_tls.der
```

## Import them into a single SoftHSM token

```bash
$ softhsm2-util --init-token --slot 0 --label "MyToken" --so-pin 1234 --pin 5678
The token has been initialized and is reassigned to slot 2129213607

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --slot 2129213607 --write-object pk_signing.der --type privkey --id 01 --label "PK_Signing"

Created private key:
Private Key Object; RSA
  label:      PK_Signing
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --slot 2129213607 --write-object cert_tls.der --type cert --id 01 --label "TLS_Cert"

Created certificate:
Certificate Object; type = X.509 cert
  label:      TLS_Cert
  subject:    DN: C=US, ST=State, L=City, O=Organization, OU=Department, CN=example.com
  serial:     18850D45C220E75A67229230F841179049B56F87
  ID:         01
```

This verifies that the private key and certificate have been imported:

```bash
$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --list-objects --slot 2129213607

Certificate Object; type = X.509 cert
  label:      TLS_Cert
  subject:    DN: C=US, ST=State, L=City, O=Organization, OU=Department, CN=example.com
  serial:     18850D45C220E75A67229230F841179049B56F87
  ID:         01
Private Key Object; RSA
  label:      PK_Signing
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive
```

## Sign a document from Java

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.SignDocument pkcs11.cfg
Document signed successfully.
```

## Showing key store contents:

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.LoadCertificates pkcs11.cfg
Alias found: TLS_Cert
Private Key Algorithm: RSA
```

# Using two slots

This is an example of using multiple PKCS#11 slots.

## Create a private key and two certificates in PEM format

```bash
rm *.pem *.der
openssl genpkey -algorithm RSA -out private_key.pem
openssl req -new -key private_key.pem -x509 -days 365 -out certificate1.pem \
  -subj "/C=US/ST=State/L=City/O=Organization/OU=Department/CN=example.com"
openssl req -new -key private_key.pem -x509 -days 365 -out certificate2.pem \
  -subj "/C=US/ST=State/L=City/O=Organization/OU=Department/CN=example2.com"
```

## Convert them into DER format

```bash
openssl pkcs8 -topk8 -inform PEM -outform DER -in private_key.pem -out private_key.der -nocrypt
openssl x509 -in certificate1.pem -outform DER -out certificate1.der
openssl x509 -in certificate1.pem -outform DER -out certificate2.der
```

## Import them into a SoftHSM token

```bash
$ softhsm2-util --init-token --slot 0 --label "MyToken" --so-pin 1234 --pin 5678
The token has been initialized and is reassigned to slot 1096489336

$ softhsm2-util --init-token --slot 1 --label "MyToken2" --so-pin 1234 --pin 5678
The token has been initialized and is reassigned to slot 2057173177

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --slot 1096489336 --write-object private_key.der --type privkey --id 01 --label "MyPrivateKey1"

Created private key:
Private Key Object; RSA
  label:      MyPrivateKey1
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --slot 1096489336 --write-object certificate1.der --type cert --id 01 --label "MyCertificate1"

Created certificate:
Certificate Object; type = X.509 cert
  label:      MyCertificate1
  subject:    DN: C=US, ST=State, L=City, O=Organization, OU=Department, CN=example.com
  serial:     40DC6BE21B75C21C582FEEB26B40F5603F8D5D3B
  ID:         01
  
$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --slot 2057173177 --write-object private_key.der --type privkey --id 02 --label "MyPrivateKey2"

Created private key:
Private Key Object; RSA
  label:      MyPrivateKey2
  ID:         02
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive
  
$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --slot 2057173177 --write-object certificate2.der --type cert --id 02 --label "MyCertificate2"

Created certificate:
Certificate Object; type = X.509 cert
  label:      MyCertificate2
  subject:    DN: C=US, ST=State, L=City, O=Organization, OU=Department, CN=example2.com
  serial:     1415A68161F90B3F02D4FA79BF946A2BEA30EB82
  ID:         02
```

This verifies that the private key and certificate have been imported:

```bash
$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --list-objects --slot 1096489336

Private Key Object; RSA
  label:      MyPrivateKey1
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive
Certificate Object; type = X.509 cert
  label:      MyCertificate1
  subject:    DN: C=US, ST=State, L=City, O=Organization, OU=Department, CN=example.com
  serial:     40DC6BE21B75C21C582FEEB26B40F5603F8D5D3B
  ID:         01
  
$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so \
  --login --pin 5678 --list-objects --slot 2057173177

Private Key Object; RSA
  label:      MyPrivateKey2
  ID:         02
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive
Certificate Object; type = X.509 cert
  label:      MyCertificate2
  subject:    DN: C=US, ST=State, L=City, O=Organization, OU=Department, CN=example2.com
  serial:     1415A68161F90B3F02D4FA79BF946A2BEA30EB82
  ID:         02
```

## Sign a document from Java

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.SignDocument pkcs11.cfg
Document signed successfully.
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.SignDocument pkcs11_2.cfg
Document signed successfully.
```

## Showing key store contents:

```bash
$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.LoadCertificates pkcs11.cfg
Alias found: MyCertificate1
Private Key Algorithm: RSA

$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.LoadCertificates pkcs11_2.cfg
Alias found: MyCertificate2
Private Key Algorithm: RSA
```

# Clean up

```bash
$ softhsm2-util --show-slots
Available slots:
Slot 1096489336
    Slot info:
        Description:      SoftHSM slot ID 0x415b1978
        Manufacturer ID:  SoftHSM project
        Hardware version: 2.6
        Firmware version: 2.6
        Token present:    yes
    Token info:
        Manufacturer ID:  SoftHSM project
        Model:            SoftHSM v2
        Hardware version: 2.6
        Firmware version: 2.6
        Serial number:    c09bd013c15b1978
        Initialized:      yes
        User PIN init.:   yes
        Label:            MyToken
Slot 2057173177
    Slot info:
        Description:      SoftHSM slot ID 0x7a9df8b9
        Manufacturer ID:  SoftHSM project
        Hardware version: 2.6
        Firmware version: 2.6
        Token present:    yes
    Token info:
        Manufacturer ID:  SoftHSM project
        Model:            SoftHSM v2
        Hardware version: 2.6
        Firmware version: 2.6
        Serial number:    761bd0367a9df8b9
        Initialized:      yes
        User PIN init.:   yes
        Label:            MyToken2
Slot 2
    Slot info:
        Description:      SoftHSM slot ID 0x2
        Manufacturer ID:  SoftHSM project
        Hardware version: 2.6
        Firmware version: 2.6
        Token present:    yes
    Token info:
        Manufacturer ID:  SoftHSM project
        Model:            SoftHSM v2
        Hardware version: 2.6
        Firmware version: 2.6
        Serial number:
        Initialized:      no
        User PIN init.:   no
        Label:
$ softhsm2-util --delete-token --token MyToken
Found token (89fc186a-637d-4396-f126-a42eab836a59) with matching token label.
The token (/opt/homebrew/var/lib/softhsm/tokens/89fc186a-637d-4396-f126-a42eab836a59) has been deleted.
$ softhsm2-util --delete-token --token MyToken2
...
```