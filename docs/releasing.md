# Releasing

A `v*` tag push runs [`release.yml`](../.github/workflows/release.yml), which signs the APK with the upload key and publishes it as a GitHub Release. This page covers the key and the four repository secrets the workflow reads; the version rules are in the Releases section of [AGENTS.md](../AGENTS.md).

## The signing key

| | |
|---|---|
| Keystore type | PKCS12 |
| Alias | `chorely` |
| Subject | `CN=Chorely, O=Woergoetter, C=AT` |
| Key | RSA 4096, `SHA384withRSA` |
| Valid | 2026-09-18 to 2054-02-03 |
| Certificate SHA-256 | `15:2E:36:B5:A8:A8:62:9D:06:65:5A:40:43:E4:0B:F7:B5:0A:39:01:4C:24:5A:33:50:A2:BB:81:61:66:41:AC` |

Android only installs an update over an existing install when both are signed with the same key. Losing the keystore or its password means no installed copy can ever be updated again: users would have to uninstall, and so lose their chores, to move to a build signed with a new key. Keep the keystore file and its password in a password manager, never in this repository.

In a PKCS12 keystore the key password is always the store password, so `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_PASSWORD` hold the same value.

## Generating a keystore

Only when there is no keystore yet, or the old one is lost for good (see above for what that costs):

```
keytool -genkeypair -keystore release.jks -storetype PKCS12 \
  -alias chorely -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Chorely, O=Woergoetter, C=AT"
```

`keytool` prompts for the password. Afterwards, update the fingerprint in the table above.

## Verifying a keystore before using it

The checks below confirm that a keystore and password are the ones that signed the published APKs. Run them before setting the secrets, because a GitHub secret can't be read back. They use `keytool` from the JDK and `apksigner` from `$ANDROID_HOME/build-tools/<version>/`.

1. Confirm the password opens the keystore and the `chorely` entry has the fingerprint above:

   ```
   keytool -list -v -keystore release.jks -alias chorely | grep SHA256
   ```

2. Sign a copy of a published APK with the keystore, the alias and the key password, then compare the result to the published original. Both commands should print the fingerprint above, written in lowercase without colons:

   ```
   gh release download v0.3.0-beta1 --pattern '*.apk'
   cp chorely-0.3.0-beta1.apk resigned.apk
   apksigner sign --ks release.jks --ks-key-alias chorely resigned.apk
   apksigner verify --print-certs chorely-0.3.0-beta1.apk | grep SHA-256
   apksigner verify --print-certs resigned.apk | grep SHA-256
   ```

## Setting the secrets

From the repository root, with `gh` logged in as an admin of the repository:

```
base64 -w0 release.jks | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_PASSWORD
gh secret set RELEASE_KEY_ALIAS --body chorely
```

Without `--body`, `gh secret set` prompts for the value, which keeps the password out of the shell history. The workflow decodes the keystore with `base64 -d`, so the value must be a single line; that is why the first command uses `-w0`.

## Cutting a release

```
git tag -a v1.2.3 -m "Chorely 1.2.3"
git push origin v1.2.3
```

A `-suffix` such as `v1.2.3-beta1` publishes a prerelease. A tag must never be moved or reused (see [AGENTS.md](../AGENTS.md)).
