# WristPrompt — narrow Google Play services linking exception

WristPrompt's base license is the GNU General Public License, version 3 only
(SPDX identifier: `GPL-3.0-only`). The canonical license text is in [LICENSE](LICENSE).
This project-specific exception is an additional permission under GPLv3 section 7.
It has no SPDX-listed exception identifier; no such identifier is claimed.

## Listed Libraries

This permission covers only the following unmodified Google-distributed artifacts
at the exact versions listed (normal Android compilation and packaging is
permitted), as required for WristPrompt's Wearable Data Layer
and Google Tasks integration on Android and Wear OS:

| Maven artifact | Version |
| --- | --- |
| `com.google.android.gms:play-services-wearable` | 20.0.1 |
| `com.google.android.gms:play-services-base` | 18.5.0 |
| `com.google.android.gms:play-services-basement` | 18.9.0 |
| `com.google.android.gms:play-services-tasks` | 18.2.0 |

“Listed Libraries” means only those artifacts, not other Google products, other
versions, modified proprietary libraries or arbitrary proprietary code. The
Wearable artifact provides the Data Layer APIs; Base, Basement and Tasks are its
resolved runtime dependencies. The coroutines Play services adapter also uses Tasks.

## Additional permission

As a special exception, the WristPrompt copyright holders give you permission
to link or combine WristPrompt with the Listed Libraries, using their Android /
Wear OS APIs for the integration described above, and to convey the resulting
executable under the terms of GNU GPL version 3, provided that you comply with all
other applicable GPLv3 requirements for WristPrompt and with the respective
licenses of the Listed Libraries.

Solely for the Listed Libraries in that combination, WristPrompt's license does
not require you to provide their Corresponding Source. This permission does not
waive the obligation to provide complete Corresponding Source for WristPrompt,
including modifications, or other applicable GPLv3 obligations for WristPrompt,
including Installation Information when required.

This additional permission applies to WristPrompt-owned material licensed under
GPL-3.0-only in this repository. It does not authorize proprietary relicensing of
WristPrompt or remove GPLv3 obligations from modifications to WristPrompt. It does
not permit linking arbitrary proprietary libraries, relicense any third-party
code, or imply that the Listed Libraries are GPL-covered. It grants no rights on
Google's or any other third party's behalf. Their licenses and notices remain
applicable; a distributor must independently comply with them.

You may extend this exception to modifications for which you have the necessary
copyright permission, but you are not obligated to do so. You may remove this
additional permission from your copy or modifications as permitted by GPLv3
section 7. A modified version without this permission must independently satisfy
the GPLv3 requirements for any library combination it conveys.

No permission is granted for separately licensed material owned by others beyond
what their own licenses allow. Adding another artifact or version to this list
requires a new license/ownership review and authorization from the relevant
WristPrompt copyright holders.
