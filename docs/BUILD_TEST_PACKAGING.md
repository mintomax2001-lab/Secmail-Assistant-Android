# BUILD_TEST_PACKAGING.md
# Mobile Build / Test / Packaging Standard

## Mandatory preflight

Before handing any new source ZIP/release to user:

`Previous Stable Diff → Dependency Review → Static Checks → ZIP/Tree Check → Workflow Check → CI Build → Package Metadata → Stable Signing → Device Test`

## Version rules
- versionCode strictly increases
- versionName matches release
- package remains `com.mintomax.secmailprobe`
- current stable signer identity must not change unintentionally

## Dependency rules
Any dependency change requires:
- reason
- diff against previous stable
- transitive dependency review
- Kotlin stdlib compatibility check

Known incident:
v0.7 first build failed because AndroidX + existing transitive Kotlin stdlib modules produced duplicate classes.

## Static checks
- Manifest XML parse
- resource XML parse
- referenced classes/resources exist
- obvious Java syntax/name collision
- FileProvider/resource consistency
- ZIP integrity if ZIP is produced

## CI evidence
Record:
- run ID
- selected source/version
- package
- versionCode/versionName
- APK SHA-256
- artifact ID

## Signing
Never expose private key/password.
Verify signer certificate matches established baseline.

## Runtime
Build success is not runtime validation.
Document exact changed-feature smoke test.
