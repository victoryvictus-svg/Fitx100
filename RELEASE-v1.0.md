# Fit100 v1.0 release candidate

Version name 1.0; Android version code 9. The higher code preserves the upgrade path from previous workspace versions.

## Install
- Existing Fit100 installation: use Fit100-v1.0-upgrade.apk. This matches the previous signing certificate and preserves local data. Debugging is disabled, but this package retains the historical debug signing identity and is intended only for migration/testing.
- New production installations: use Fit100-v1.0.apk. This has a separate RSA 3072-bit release certificate, debugging disabled and Android backup disabled. It cannot replace the earlier debug-signed app in place. Do not uninstall an existing installation expecting local data/photos to survive.

Production certificate SHA-256: 466f721f344c1cbe3cbd60249ebd6fe3b7e0a74b3eba5d525c51b27a9d43780b.

## Signing material
The release key and its password are stored in .release-private/ with access restricted to the current Windows identity. This folder is excluded from Git. Back up this folder securely before distribution. Never publish or include it in a source archive. Keep the same key for future production updates. No secret values are embedded in the APK. No APK has been uploaded or published.

Build from the workspace root: `python Fit100Android/tools/build_apk.py`. Add `--qa` only for a debug-enabled QA package. The offline builder uses the locally cached Kotlin compiler, Android SDK and signing tools; it produces separate release and existing-install upgrade packages. Gradle plugin resolution remains unavailable in the current offline cache, so this release was built and signed with the documented offline pipeline.

## Behavior
Start My 100-Day Challenge saves the current epoch timestamp. Day 1 starts immediately at 0%; each full 24 hours advances the day. Completion occurs at exactly 100 elapsed days, independent of daylight-saving transitions. The timestamp persists across reopen/reboot. Older date-only preferences no longer start a challenge automatically. Existing saved challenge timestamps are preserved. The former hard-coded default date is not treated as an actual start. Start time is fixed for this challenge. Progress refreshes on resume and once per minute while visible; whole percentages represent elapsed time. The device clock remains the time authority; offline operation cannot prevent manual clock manipulation.

All photo slots offer camera and gallery. Camera permission is requested on demand, denial offers Android Settings, and the gallery uses the system document picker without broad storage access. Camera files use temporary grants through a non-exported provider. New gallery selections are validated and copied into private app storage (30 MB encoded-size and 100 MP dimension limits). Replacing a slot changes that slot's album entry. The album groups all saved dated before/after/front/side/back slots and older numbered photos, with filters, pagination and an in-app image viewer. Profile portraits remain separate. Legacy gallery links still depend on their original document provider. Uninstalling removes private files; automatic backup is disabled.

## Verification and remaining release gate
Kotlin/API 35 compilation, signature verification, non-debuggable APK inspection and challenge/album boundary tests passed. API 37 emulator checks verified first start, persisted timestamp, camera permission grant, full-resolution capture, private-URI save, album rendering, in-app viewing, gallery import into private storage, compatible non-debuggable upgrade preserving records, permission denial with a Settings recovery dialog, and the album at 320 dp with 1.3x text. No Fit100 crash appeared in the checked runtime log. The emulator had a cold-boot System UI ANR before the app checks.

This is a signed release candidate, not a claim of complete production certification. Physical Pixel/Samsung camera/provider testing, broader OS/device regression, release-key backup and distribution-account/store preparation remain necessary before public launch. No exhaustive security audit or store submission was performed. Existing app limitations documented in README remain applicable.


## Sports update (build 9)
The Sports tab supports over 50 activities and Other / Custom. Users can time an activity or record a dated session with duration, optional distance and notes (scores, laps, sets or route). The timer persists its start timestamp and continues counting while the app is closed; saving is explicit. History supports deletion and pagination; a seven-day summary shows sessions and minutes. This is manual/timer tracking; GPS routes, step sensors, heart-rate/wearable integrations and sport-specific score engines are not implemented. CSV export includes saved session records.

Start Challenge is visible in a card at the top of Home and on Profile before starting. Logging sports, opening Goals, launching the app or an old date-only preference does not start the challenge. A previously started challenge remains started, and repeated start calls cannot reset its timestamp.
