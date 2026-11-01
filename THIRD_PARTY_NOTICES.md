# Third-party notices and direct production dependency review

Reviewed 2026-10-30 against `gradle/libs.versions.toml`, both application dependency
blocks and locally resolved artifact metadata. WristPrompt's own code is
GPL-3.0-only. This does not relicense dependencies or the Gradle wrapper.

## Compatibility result

**PASS — GPLv3 WITH NARROW LINKING EXCEPTION.** WristPrompt remains GPL-3.0-only
with the additional permission in [LICENSE_EXCEPTION.md](LICENSE_EXCEPTION.md).
The canonical GPLv3 text in LICENSE is unchanged. This is a project-specific
permission under GPLv3 section 7, not an SPDX-listed exception or a relicense of
the Google libraries. No System Libraries assumption is required.

Both apps directly declare `com.google.android.gms:play-services-wearable:20.0.1`,
the artifact supplying Wearable Data Layer APIs. Offline Gradle
`releaseRuntimeClasspath` reports and existing debug/release resolved dependency
models agree on exactly these proprietary artifacts:

| Google artifact | Resolved version | License | Permission scope |
| --- | --- | --- | --- |
| `com.google.android.gms:play-services-wearable` | 20.0.1 | Android SDK License | Listed in exception; Data Layer client |
| `com.google.android.gms:play-services-base` | 18.5.0 | Android SDK License | Listed in exception; Wearable dependency |
| `com.google.android.gms:play-services-basement` | 18.9.0 | Android SDK License | Listed in exception; shared dependency |
| `com.google.android.gms:play-services-tasks` | 18.2.0 | Android SDK License | Listed in exception; Wearable and coroutines adapter |

All four resolved POMs link to the [official SDK terms](https://developer.android.com/studio/terms).
The coroutines adapter requests Tasks 16.0.1, upgraded by resolution to 18.2.0;
Base requests Basement 18.4.0, resolved to 18.9.0. The permission covers the actual
selected versions only. Neither other Google products nor future versions are
implicitly covered. Library upgrades require a renewed license/scope review.
Google's AAR third-party license files do not relicense all client code; their
notices and the SDK terms continue to apply. No dependency is converted to GPL.

The owner's section 7 permission follows the conventional approach described in
[GNU's incompatible-library guidance](https://www.gnu.org/licenses/gpl-faq.en.html#GPLIncompatibleLibs).
It does not waive Corresponding Source for WristPrompt/modifications, installation
information when required, or any other applicable GPLv3 obligation for WristPrompt.
It gives no redistribution permission on Google's behalf. This publication contains
project source only, not Google AARs or application binaries.

The permissive dependencies below have no identified incompatibility with GPLv3.
Apache-2.0, BSD-2-Clause, BSD-3-Clause and MIT are generally compatible with GPLv3;
MPL/LGPL and other GPL versions require their exact terms/combination to be checked.
No direct MPL, LGPL, BSD-3-Clause or MIT dependency was declared in these app blocks.
The bounded follow-up also reviewed 97 distinct resolved production artifacts
across both apps' release package models. Ninety are Apache-2.0 (including Guava
ListenableFuture 1.0 through guava-parent 26.0-android), two are BSD-2-Clause
(CommonMark), one is BSD-3-Clause (`androidx.datastore:datastore-preferences-external-protobuf:1.3.0`), and four are
the proprietary artifacts listed above. POM metadata provides the license classes;
the CommonMark table extension was verified in its embedded JAR notice. No other
GPL-incompatible or unresolved artifact license was identified in that set.
This is not an exhaustive binary transitive notice bundle.
A later binary distribution must also preserve all required transitive notices.
Google's [notice guidance](https://developers.google.com/android/guides/opensource)
places notice compliance responsibility on app developers.

| Dependency | Pinned version | Declared license | Review |
| --- | --- | --- | --- |
| `androidx.activity:activity-compose` | 1.13.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.compose.ui:ui` | 1.12.1 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.compose.foundation:foundation` | 1.12.1 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.compose.material3:material3` | 1.4.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.wear.compose:compose-material3` | 1.7.1 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.wear.compose:compose-foundation` | 1.7.1 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.11.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `com.google.android.gms:play-services-wearable` | 20.0.1 | Android SDK License (proprietary) | Covered by narrow project linking exception; SDK license remains unchanged. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `com.squareup.okhttp3:okhttp` | 5.5.1 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `com.nimbusds:nimbus-jose-jwt` | 10.10 | Apache-2.0 | No identified GPLv3 incompatibility; preserve license/notices. |
| `org.commonmark:commonmark` | 0.31.0 | BSD-2-Clause | No identified incompatibility; full notice below. Verified in both JARs. |
| `org.commonmark:commonmark-ext-gfm-tables` | 0.31.0 | BSD-2-Clause | No identified incompatibility; full notice below. Verified in both JARs. |
| `org.jetbrains.kotlin:kotlin-stdlib` (compiler runtime) | 2.4.21 | Apache-2.0 | No identified GPLv3 incompatibility; preserve notices. |

All Apache rows above were verified in local POM metadata; the CommonMark table
extension omits a license element in its own POM, so its **embedded JAR
META-INF/LICENSE.txt** was checked instead. It exactly matches the core's notice
below. Both actual pinned artifacts are 0.31.0, with no extra Markdown library.

The Gradle wrapper scripts keep their Apache-2.0 upstream headers unchanged.
The wrapper is build tooling, not WristPrompt project licensing. Pinned Gradle,
AGP and compiler tooling retain their own licenses; SDK acceptance is a separate
build prerequisite. No third-party file is claimed as relicensed by root LICENSE.

## CommonMark core and GFM tables — BSD-2-Clause

Applies to `org.commonmark:commonmark:0.31.0` and
`org.commonmark:commonmark-ext-gfm-tables:0.31.0`. Binary redistributions must carry
this notice/conditions in accompanying documentation or distribution materials.

Copyright (c) 2015, Atlassian Pty Ltd
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## D7-C EXIF metadata reader — 2026-11-01

Both apps additionally pin `androidx.exifinterface:exifinterface:1.4.2` (Apache-2.0).
The [official stable release](https://developer.android.com/jetpack/androidx/releases/exifinterface)
and [AndroidX source license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/exifinterface/exifinterface/src/main/java/androidx/exifinterface/media/ExifInterface.java)
were reviewed. Preserve its Apache license/notices; it does not require extending
the Google Play services linking exception. This is a metadata reader for bounded
image import, replacing the old platform parser flagged by Android lint for known
security bugs; it is not a general image-loading framework. Existing pins remain unchanged.
