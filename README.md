# PKCS#11 example

Minimal example of using PKCS#11 from Java without a real smart card.

# Setup

These instructions have been tested on MacOS using an M1 Mac Book Pro.

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

# Create a private key and certificate in PEM format

```bash
$ openssl genpkey -algorithm RSA -out private_key.pem
...............+......+.+++++++++++++++++++++++++++++++++++++++*...+.....+............+.+..+....+...+......+.....+....+...........+...............+..........+......+......+..+...+....+...+...+..............+......+.......+.....+.+..+++++++++++++++++++++++++++++++++++++++*.+......+..+......+......+.+............+......+..+......................+......+.....+.......+.....+...+....+.....+............+....+.....+....+..................+...+..+...+.........+.......+........+.......+......+..+.+.........+......+......+.....+...+.+.................+..................+.......+............+......+.........+......+.....+.+.....+.........+......+...+.+...........+.+.....+..........+...+..............+...+.........+..........+...........+...+......+...+......+.++++++
.....+......+........+++++++++++++++++++++++++++++++++++++++*...+....+...+........+.......+..+......+.......+........+....+..+++++++++++++++++++++++++++++++++++++++*.......+.+........+.+...........+.........+.........+.......+.....+.+...+...........+......+....+.....+....+.....+.+...+.....+.............+.................+.+..+.......+.....+...+....+...+...........+.......+............+..+...+.+.....+.+........+.+...........+...+.......+.....+.+...+......+........+...+....+.........+..+.......+........+....+...+...........+.+...+...+..+...+...+..........+...........+...+......+.......+........+.+......+........+................+........+.+..+...+.+........+.......+...+..+...............+.+..+...+...+.........+......+..........+.........+..+..................+...+.......+.....+.+............+..............+...+.+.........++++++
$ openssl req -new -key private_key.pem -x509 -days 365 -out certificate.pem -subj "/C=IT/ST=IT/L=BO/O=ACME/OU=IT/CN=example.com"
```

# Convert them into DER format

```bash
$ openssl pkcs8 -topk8 -inform PEM -outform DER -in private_key.pem -out private_key.der -nocrypt
$ openssl x509 -in certificate.pem -outform DER -out certificate.der
```

# Import them into a SoftHSM token

```bash
$ softhsm2-util --init-token --slot 0 --label "MyToken" --so-pin 1234 --pin 5678
The token has been initialized and is reassigned to slot 889004786

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so --login --pin 5678 --write-object private_key.der --type privkey --id 01 --label "MyPrivateKey"
Using slot 0 with a present token (0x34fd22f2)
Created private key:
Private Key Object; RSA
  label:      MyPrivateKey
  ID:         01
  Usage:      decrypt, sign, signRecover, unwrap
  Access:     sensitive

$ pkcs11-tool --module /opt/homebrew/Cellar/softhsm/2.6.1/lib/softhsm/libsofthsm2.so --login --pin 5678 --write-object certificate.der --type cert --id 01 --label "MyCertificate"
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

# Sign a document from Java

```bash
$ mvn clean install
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  2.753 s
[INFO] Finished at: 2024-06-14T12:35:24+02:00
[INFO] ------------------------------------------------------------------------

$ java -cp target/pkcs11-1.0-SNAPSHOT.jar org.example.SignDocument
Document signed successfully.
```

# Clean up

```bash
$ softhsm2-util --show-slots
Available slots:
Slot 889004786
    Slot info:
        Description:      SoftHSM slot ID 0x34fd22f2
        Manufacturer ID:  SoftHSM project
        Hardware version: 2.6
        Firmware version: 2.6
        Token present:    yes
    Token info:
        Manufacturer ID:  SoftHSM project
        Model:            SoftHSM v2
        Hardware version: 2.6
        Firmware version: 2.6
        Serial number:    fd3ebba434fd22f2
        Initialized:      yes
        User PIN init.:   yes
        Label:            MyToken
$ softhsm2-util --delete-token --token MyToken
Found token (89fc186a-637d-4396-f126-a42eab836a59) with matching token label.
The token (/opt/homebrew/var/lib/softhsm/tokens/89fc186a-637d-4396-f126-a42eab836a59) has been deleted.
```