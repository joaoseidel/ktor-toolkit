# Releasing

Artifacts are published to Maven Central under `io.github.joaoseidel`, one artifact per module:

```kotlin
implementation("io.github.joaoseidel:ktor-toolkit-paginator:1.0.0")
```

The group has to live under `io.github.<user>`. Maven Central only issues a GitHub-verified namespace in that form; `com.github.joaoseidel` is not
registrable, whatever the Kotlin packages happen to be called.

## Cutting a release

1. Bump `version` in `gradle.properties` to the version you are releasing.
2. Move the `unreleased` heading in `CHANGELOG.md` to that version. The release notes are taken verbatim from the `## [<version>]` section, so this is
   what people will read.
3. Confirm the tree is clean:

   ```bash
   make verify
   ```

4. Commit, merge to `main`, then tag, and push:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

5. Approve the run if the `maven-central` environment has required reviewers.
6. Bump `version` in `gradle.properties` to the *next* version. Snapshot publishing from `main`
   stops until you do: it refuses to publish a snapshot for a version that is already tagged.

Everything after the tag push is `.github/workflows/release.yml`.

## What the release workflow does

**Verify** runs before the environment gate, so a reviewer is only ever asked about a tag that already passes. It:

- refuses a tag that disagrees with `version` in `gradle.properties`, and refuses `-SNAPSHOT`;
- refuses a version already on Maven Central. Central is append-only, so a coordinate can never be replaced; this is checked before anything is built
  rather than discovered from a rejected deployment;
- runs `make verify`: ktlint, `apiCheck`, the full test suite, and the 100% line/branch coverage gate.

**Publish** is gated on the `maven-central` environment, which is where the credentials live. It:

- builds the jars with `make dist`, so the bytes uploaded to Central, attested, and attached to the GitHub release are one set of files;
- signs and uploads with `make publish`;
- records a build provenance attestation;
- creates the GitHub release from the `CHANGELOG.md` section, falling back to generated notes.

## Snapshots

`.github/workflows/snapshot.yml` publishes `<version>-SNAPSHOT` on every push to `main`, after the same `make verify`. `gradle.properties` therefore
holds the *next* version to be released, and the workflow skips itself once `v<version>` exists; that is the reminder to bump.

```kotlin
repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}
```

## Required secrets

Set these on the `maven-central` and `maven-central-snapshots` environments, not as repository secrets, so a workflow outside those environments
cannot reach them.

| Secret                   | Where it comes from                                                                                     |
|--------------------------|---------------------------------------------------------------------------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | Central Portal → Account → Generate User Token: the token's *username*, a random string, not your login |
| `MAVEN_CENTRAL_PASSWORD` | the password half of the same user token                                                                |
| `SIGNING_KEY`            | the ASCII-armored private key, from step 4 below                                                        |
| `SIGNING_KEY_ID`         | the last 8 characters of the key id, from step 2                                                        |
| `SIGNING_PASSWORD`       | the passphrase chosen in step 1                                                                         |

## Generating the signing key

Central will not accept an unsigned artifact, and it checks the signature against a public key it fetches from a keyserver. One GPG key produces three
of the five secrets above. This is a one-time setup; skip to the next section if the key already exists.

### 1. Generate it

```bash
gpg --full-generate-key
```

Answer `1` (RSA and RSA), `4096`, and `0` for the expiry: an expired key means a future release fails validation for no useful reason. Use the same
name and email as the `<developer>` block in the POM.

It then asks for a passphrase. **That passphrase is `SIGNING_PASSWORD`**; it cannot be recovered, so record it before continuing.

### 2. Read the key id

```bash
gpg --list-secret-keys --keyid-format=long
```

```
sec   rsa4096/A1B2C3D4E5F6A7B8 2026-08-06 [SC]
      1234567890ABCDEF1234567890ABCDEF12345678
uid                 [ultimate] João Seidel <joaovseidel@gmail.com>
```

`A1B2C3D4E5F6A7B8` (the part after the slash on the `sec` line) is the long key id used in the commands below. **`SIGNING_KEY_ID` is its last 8
characters**, `E5F6A7B8`; Gradle matches on the short form and passing all 16 can fail to resolve.

### 3. Publish the public half

The key has to be on a keyserver before the first release, or Central cannot check the signature:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys A1B2C3D4E5F6A7B8
```

Use this keyserver specifically. `keys.openpgp.org` strips the user id until the address is confirmed by email, which trips up validation.

### 4. Export the private half

`SIGNING_KEY` is the whole armored block, `-----BEGIN` and `-----END` lines included:

```bash
gpg --armor --export-secret-keys A1B2C3D4E5F6A7B8 | pbcopy
```

Add `--pinentry-mode loopback` if pinentry cannot prompt in your terminal.

### 5. Load the secrets

Create the environments:

```bash
gh api -X PUT repos/joaoseidel/ktor-toolkit/environments/maven-central
```

```bash
gh api -X PUT repos/joaoseidel/ktor-toolkit/environments/maven-central-snapshots
```

Piping the key straight in keeps it off disk and out of shell history. Repeat for
`--env maven-central-snapshots`:

```bash
gpg --armor --export-secret-keys A1B2C3D4E5F6A7B8 | gh secret set SIGNING_KEY --env maven-central
```

`gh secret set` with no value prompts for it with the input hidden, which is how the remaining four should be entered:

```bash
gh secret set SIGNING_PASSWORD --env maven-central
```

### 6. Keep a revocation certificate

Generate this now, while the key is uncompromised, and store it somewhere other than this machine:

```bash
gpg --output ktor-toolkit-revoke.asc --gen-revoke A1B2C3D4E5F6A7B8
```

### 7. Prove it signs

Put `signingInMemoryKey`, `signingInMemoryKeyId` and `signingInMemoryKeyPassword` in
`~/.gradle/gradle.properties`. Gradle reads that file and the one at the repository root, and nothing else, the repository's `.gradle/` directory is a
build cache, so properties dropped there are silently ignored and signing fails with *no configured signatory*.

Three details decide whether the key parses at all:

- The value has to be a single logical line. End each line of the armored block with `\n\`: the `\n`
  is the newline, the trailing `\` continues the line into the next one.
- Keep the empty line `gpg` prints between `-----BEGIN PGP PRIVATE KEY BLOCK-----` and the base64, as `\n\n\` on that first line. Without it the
  header runs into the body and the block will not decode.
- `signingInMemoryKeyId` is the *last 8 characters* of the key id, as in step 2. Passing all 16 is rejected outright: *The key ID must be in a valid
  form (eg 00B5050F or 0x00B5050F)*.

Both parse failures surface as the same unhelpful *Could not read PGP secret key*. Then:

```bash
./gradlew publishToMavenLocal --no-configuration-cache
```

```bash
ls ~/.m2/repository/io/github/joaoseidel/ktor-toolkit-cache/1.0.0/*.asc
```

There should be one `.asc` per artifact. If the directory is empty, signing silently did nothing and the deployment would be rejected after the
upload.

## Verifying a published artifact

The provenance attestation ties a jar back to the workflow run and commit that produced it:

```bash
gh attestation verify ktor-toolkit-paginator-1.0.0.jar --repo joaoseidel/ktor-toolkit
```

## Publishing from a workstation

Not the supported path: it skips the tag check, the attestation, and the environment gate, but useful when diagnosing a publishing failure. On top of
the three signing properties from step 7, add
`mavenCentralUsername` and `mavenCentralPassword` to `~/.gradle/gradle.properties`, then:

```bash
make publish
```

To stage a deployment for manual review in the Portal instead of releasing it automatically, set
`mavenCentralAutomaticPublishing=false` in `gradle.properties`.

## Troubleshooting

**`Configuration cache problems found`**: the publishing tasks upload over the network and are not configuration-cache compatible. `make publish` and
`make publish_local` already pass
`--no-configuration-cache`; a raw `./gradlew publishToMavenCentral` needs it too.

**Deployment rejected for a missing signature or javadoc**: run the same path locally with
`make publish_local` and check the artifact list. This is what CI's Publication job guards against.

**Namespace not verified**: the group must be exactly `io.github.joaoseidel`, and that namespace has to be claimed once in the Central Portal against
the matching GitHub account.

**`No public key` / signature not validated**: the public half never reached a keyserver, or propagation has not finished. Confirm it is there before
retrying:

```bash
gpg --keyserver keyserver.ubuntu.com --recv-keys A1B2C3D4E5F6A7B8
```
