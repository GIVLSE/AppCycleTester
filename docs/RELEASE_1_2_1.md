# Sequmio 1.2.1 (22)

Profile: compact Sign out action to the right of name/email; full-width red pill-shaped Delete my Sequmio account button below the profile. Existing confirmation retained.

First publish fix: CommunityRepository.savePublished reads apps/{packageName} before writing a new listing and its scenario snapshot. The old get rule denied a missing resource because its fields do not exist. Allow authenticated get of a nonexistent app document, retain owner-only draft reads and writes, and retain atomic version snapshot ownership checks. Existing catalog list access unchanged. Align local publication validation with the existing Firestore URL and length checks.

Deploy the updated firestore.rules to the real Firebase project after emulator regression tests. No production deployment is performed by the Android build. The server rule fixes the initial read even for v1.2.0. See tests/firestore-publication.cjs (12 regression tests); also rerun previous 36 checks in the Cloud Shell workspace. Device retest first publish and repeat/update publication. No changes to auth, report upload or deletion business logic.
