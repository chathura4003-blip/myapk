# MYAPK PRO-LEVEL MASTER PROMPT
## COMPLETE EXISTING PROJECT REPAIR + SECURITY + PERFORMANCE + NATIVE INTEGRATION + ADMIN SEPARATION + FINAL RELEASE



============================================================
ROLE
============================================================

Act as a senior:

Android Architect
Kotlin Engineer
JavaScript Engineer
Capacitor Engineer
Media3 / ExoPlayer Engineer
Download Engine Engineer
VPN / VLESS / Xray Engineer
Torrent Engineer
Google Drive / OAuth Engineer
Room / DataStore Engineer
WebView Security Engineer
Backend/API Engineer
Performance Engineer
QA Engineer
Release Engineer

You are working on the EXISTING project.

DO NOT create a replacement project.

DO NOT rewrite working modules unnecessarily.

DO NOT make the application "look fixed".

MAKE THE APPLICATION ACTUALLY WORK.

============================================================
CORE RULE
============================================================

Before modifying ANY module:

1. Inspect current implementation.
2. Identify actual bug.
3. Identify root cause.
4. Check whether the feature already has a working implementation.
5. Fix the existing implementation where possible.
6. Do not create duplicate systems.
7. Build.
8. Test.
9. Review diff.
10. Re-test.

Never claim a feature is fixed without testing.

Never replace real functionality with:

mock
fake
dummy
simulation
placeholder
random values
fake state

============================================================
TARGET ARCHITECTURE
============================================================

FINAL USER APK:

Android APK
│
├── Capacitor / local UI
├── Kotlin Native Layer
│
├── Room
├── DataStore
├── Native Download Service
├── Media3 / ExoPlayer
├── Browser/WebView
├── Google Drive integration
├── VpnService + supported native VPN core
├── Torrent Engine
├── Native Extractor
└── legitimate HTTPS APIs

USER APK MUST NOT REQUIRE:

Node.js
Express
server.js
localhost backend
127.0.0.1 backend
development server

except for a narrowly justified local native proxy if one is genuinely required and securely isolated.

ADMIN SYSTEM:

Keep Admin Web Panel / Admin backend separate.

Create:

Admin Web
+
Admin API / trusted backend
+
Admin APK

Do NOT remove the Admin backend merely because the User APK becomes standalone.

============================================================
PHASE 01 — FULL REPOSITORY AUDIT
============================================================

Inspect:

android/
public/
bin/
lib/
jni/
package.json
package-lock.json
capacitor.config.json
all Kotlin
all Java
all JS
all CSS
all HTML
all XML
all Gradle
all resources
all native libraries
all CI workflows

Search globally:

server.js
admin-panel.js
localhost
127.0.0.1
/api/
API_BASE
serverBase
socket
socket.io
WebSocket
proxy
worker
Colab
Vercel
Railway
Docker
Cloudflare
license
OTA
Google Drive
OAuth
VLESS
V2Ray
torrent
download
player
gallery
settings

Also search:

TODO
FIXME
HACK
mock
fake
dummy
placeholder
Math.random()
return true
return false
success:true
connected:true
completed:true

Create:

docs/phase01-complete-audit.md

For every finding:

FILE
FUNCTION
BUG
ROOT CAUSE
SEVERITY
DEPENDENCY
PROPOSED FIX
TEST REQUIRED

DO NOT make destructive changes in this phase.

============================================================
PHASE 02 — BUILD BASELINE
============================================================

Inspect package.json.

Determine actual scripts.

Do not invent:

npm run build

unless it exists.

Determine correct commands.

Verify:

Node version
npm version
Java version
Gradle
AGP
Kotlin
compileSdk
targetSdk
minSdk
applicationId
versionCode
versionName

Build current project.

Record:

compile errors
Gradle errors
lint errors
test errors

Create:

docs/phase02-build-baseline.md

============================================================
PHASE 03 — STANDALONE USER APK
============================================================

Make the User APK independent from Node runtime.

Remove runtime dependencies on:

Node
Express
server.js
local backend
localhost APIs

Audit every:

fetch
axios
WebSocket
Socket.IO
/api/

For every API:

OLD
→
NATIVE / LOCAL / LEGITIMATE EXTERNAL

Do NOT convert backend calls into fake fetch interception.

Use:

Kotlin native services
Room
DataStore
Capacitor bridge
Android APIs
legitimate HTTPS APIs

After migration:

search again for:

localhost
127.0.0.1
/api/
API_BASE
serverBase
server.js

Every remaining match must be documented.

============================================================
PHASE 04 — KEEP ADMIN SYSTEM SEPARATE
============================================================

IMPORTANT:

Do NOT break the Admin backend.

The target is:

USER APK
=
standalone

ADMIN SYSTEM
=
separate trusted system

ADMIN WEB
+
ADMIN API
+
ADMIN APK

Inspect current:

server.js
admin-panel.js
admin panel routes
admin authentication
admin database

Do not move private admin credentials into the User APK.

Admin APK should communicate with the trusted Admin API only.

Do not embed admin password or private API secrets in the APK.

Admin APK should be able to manage:

Users
Devices
Licenses
Subscriptions
App versions
OTA metadata
Content/source configuration
System settings
Logs
Audit events

Use role-based authorization.

Admin API must independently verify admin privileges.

============================================================
PHASE 05 — MOCK / FAKE STATE REMOVAL
============================================================

Search ALL runtime code.

Remove or replace:

fake accounts
fake Drive state
fake quota
fake premium
fake license
fake VPN state
fake speed
fake ping
fake progress
fake completion
fake OTA
fake movie metadata
fake ratings
fake views

REAL DATA ONLY.

Unknown:

--
or
Unknown

Never invent.

============================================================
PHASE 06 — SECRET / CREDENTIAL SECURITY
============================================================

Search source for:

OAuth client secrets
API keys
private keys
passwords
JWT secrets
refresh tokens
access tokens
license signing secrets

Any private credential ever committed to public source control must be treated as compromised.

Rotate/revoke externally where necessary.

Remove from:

JS
Kotlin
Gradle
JSON
APK assets

Never put replacement private secrets inside the APK.

Create:

docs/phase06-secret-audit.md

Do not include secret values.

============================================================
PHASE 07 — WEBVIEW SECURITY
============================================================

Audit:

MainActivity
Capacitor config
network security
WebView settings
JS bridges
URL navigation

Required:

WebView debugging OFF in release.

No global SSL bypass.

Never:

SslErrorHandler.proceed()

for certificate errors.

Certificate errors:

BLOCK
+
REAL ERROR

Review:

JavaScript
DOM storage
file access
content access
third-party cookies
mixed content
navigation
downloads
custom schemes

Minimize unsafe configuration.

Do not globally expose arbitrary native methods.

============================================================
PHASE 08 — THIRD-PARTY PLAYER / PROXY SECURITY
============================================================

Audit all source-specific WebView/proxy logic.

Especially:

Referer injection
CSP rewriting
X-Frame-Options rewriting
CORS header injection
HTML rewriting
external iframe fallback

Never create a global "unblock everything" mechanism.

Use:

strict host allowlist
+
specific path rules
+
specific integration behavior

Unknown domain:

BLOCK

Do not trust arbitrary HTML as native input.

============================================================
PHASE 09 — MANIFEST / PERMISSION MODERNIZATION
============================================================

Audit:

AndroidManifest.xml

Review:

READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
READ_MEDIA_VIDEO
READ_MEDIA_IMAGES
READ_MEDIA_AUDIO
GET_ACCOUNTS
MANAGE_EXTERNAL_STORAGE
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
WAKE_LOCK
POST_NOTIFICATIONS

For every permission:

identify exact feature.

Migrate to:

MediaStore
Storage Access Framework
app-specific storage
content:// URIs

Remove permissions that are not required.

Do not blindly remove a permission before testing its feature.

Remove:

requestLegacyExternalStorage

when modern storage behavior makes it unnecessary.

Remove:

largeHeap

unless profiling proves it is required.

Notification permission should preferably be requested in feature context rather than unnecessarily on first launch.

============================================================
PHASE 10 — NATIVE BRIDGE
============================================================

Audit all Capacitor plugins.

Create clean interfaces:

NativeStorage
NativeDownloads
NativeMedia
NativeExtractor
NativeTorrent
NativeVPN
NativeDrive
NativeBrowser
NativeLicense
NativeUpdates

Each native method:

validates input
runs on correct thread
returns structured response
propagates error
does not leak resources

Response:

{
  success,
  data,
  errorCode,
  errorMessage
}

Never expose:

arbitrary command execution
arbitrary filesystem access
raw shell commands

============================================================
PHASE 11 — STATE ARCHITECTURE
============================================================

Persistent source of truth:

Room / native state.

JavaScript state:

presentation cache only.

Fix:

optimistic state
stale state
event races
duplicate state stores
out-of-order events

Native failure:

UI must revert.

Native success:

UI updates from real state.

============================================================
PHASE 12 — DOWNLOAD SYSTEM
============================================================

Audit:

downloads.js
NativeDownloadPlugin
DownloadForegroundService
Room
downloader
torrent integration

States:

QUEUED
STARTING
CONNECTING
DOWNLOADING
PAUSED
COMPLETED
FAILED
CANCELLED

Fix:

start
pause
resume
retry
cancel
delete
open
play
share
notification
progress
speed
ETA
background
process death
restart
network loss
network recovery
storage failure

Use stable download IDs.

Use Room as persistent source of truth.

No fake:

speed
ETA
progress
completion

============================================================
PHASE 13 — DOWNLOAD RANGE / RESUME
============================================================

For resumable HTTP:

validate:

Accept-Ranges
206 Partial Content
Content-Range

If range request returns:

200

DO NOT append blindly.

Handle:

416

correctly.

Prevent corruption.

For unsupported resume:

restart safely or report unsupported.

============================================================
PHASE 14 — TORRENT SEPARATION
============================================================

Mandatory:

HTTP/HTTPS
→ Native HTTP downloader

magnet:
→ Native torrent engine

.torrent
→ torrent engine

NEVER:

magnet
→ HTTP downloader

If torrent engine fails:

report real torrent error.

Do not rename magnet into:

.torrent

and pretend download occurred.

No fake peer count.

No fake torrent speed.

============================================================
PHASE 15 — DOWNLOAD UI
============================================================

Remove misleading marketing telemetry.

Do not display:

10 Gbps
8-thread turbo
instant
unlimited

unless it is genuinely measured.

Display:

real speed
real progress
real ETA
real bytes

If unavailable:

--

or

Calculating...

Use event-driven native updates.

Polling only as fallback.

============================================================
PHASE 16 — DOWNLOAD DELETE / RECOVERY
============================================================

Delete sequence:

Native operation
↓
success
↓
Room update
↓
UI update

On failure:

keep record
+
show error

Never:

remove UI first
+
silently fail

After app restart:

read Room
+
verify actual file
+
recover states

Process death:

recover where possible.

No duplicate task creation.

============================================================
PHASE 17 — MOVIE SEARCH / EXTRACTION
============================================================

Audit:

movies.js
standalone-engine
NativeExtractor
provider modules
resolvers

Fix:

duplicate results
bad metadata
dead source
timeouts
retry loops
invalid URL
wrong quality
stale selectors

Normalize:

id
title
thumbnail
source
url
duration
quality
language

Do not fabricate metadata.

Only use content sources the app is legitimately authorized to access.

Do not bypass DRM/paywall/authentication protection.

============================================================
PHASE 18 — MOVIE STREAMING
============================================================

Use:

Media3 / ExoPlayer

for supported direct media.

Routing:

Local file
→ Media3

Direct media
→ Media3

HLS
→ Media3 HLS

DASH
→ Media3 DASH

Embed
→ controlled WebView

Optimize:

buffer
startup
rebuffering
audio focus
PiP
subtitles
audio track
resume
orientation

Evaluate:

HttpEngine / Cronet

where appropriate.

Use adaptive bitrate where the source supports it.

============================================================
PHASE 19 — STREAMING PERFORMANCE
============================================================

Implement where beneficial:

connection reuse
cache
preload
adaptive bitrate
bounded retry

Do not preload huge content.

Do not increase memory/battery usage unnecessarily.

Measure:

startup time
rebuffer count
time to first frame
bandwidth utilization

where practical.

============================================================
PHASE 20 — 18+ EXPERIENCE
============================================================

Keep this area age-restricted and compliant.

Require appropriate adult access/age gating before loading the restricted content.

Recommendation data should remain local by default.

Do not use:

GPS
VPN region
IP region

as hidden taste signals.

Do not recommend or classify content involving minors or ambiguous-age persons.

Remove unsafe age-coded taxonomy.

============================================================
PHASE 21 — 18+ RECOMMENDATION ENGINE
============================================================

Use one coherent pipeline:

EVENT
↓
PROFILE
↓
CANDIDATES
↓
FILTER
↓
SCORE
↓
NEGATIVE FEEDBACK
↓
EXPOSURE PENALTY
↓
DIVERSITY
↓
EXPLORATION
↓
FINAL RANK

Track:

impression
click
play
watch duration
completion
replay
skip
like
dislike
not interested
save
download

Sustained watch behavior must have stronger influence than simple clicks.

Do not double-count event chains from one interaction.

Use:

completion ratio
source affinity
quality preference
duration preference
freshness
negative feedback
recent exposure
diversity
controlled exploration

Avoid uncontrolled random ranking.

============================================================
PHASE 22 — 18+ FEED FEATURES
============================================================

Provide:

For You
Continue Watching
Because You Watched
Fresh
Trending
Preferred Sources
Explore
Up Next

Add:

Like
Dislike
Not Interested
Hide Source
Undo

Continue Watching stores:

itemId
position
duration
lastWatchedAt

Resume accurately.

Do not count idle/background time as watch time.

============================================================
PHASE 23 — 18+ METADATA INTEGRITY
============================================================

NEVER invent:

1080p
720p
480p
4K
HD
rating
views
duration
trending

unless confirmed by source metadata.

If missing:

Unknown
--

Never create multiple fake quality choices pointing to one unknown URL.

============================================================
PHASE 24 — 18+ DOM SECURITY
============================================================

Audit dynamic HTML.

Do not inject untrusted:

title
source
thumbnail
URL
error message

directly through innerHTML when safer APIs are available.

Use:

textContent
safe attribute assignment
URL validation
safe DOM construction

Prevent:

XSS
javascript:
data:
file:

unless intentionally and safely supported.

============================================================
PHASE 25 — 18+ REQUEST CONTROL
============================================================

Fix:

search race conditions
feed race conditions
duplicate loading
retry loops
infinite scroll duplication

Use:

requestId
AbortController
loading lock
page guard
maximum retry count

Latest request wins.

Old requests must not overwrite new state.

============================================================
PHASE 26 — GOOGLE DRIVE
============================================================

Audit:

OAuth
callback
token refresh
logout
account switching
file listing
folders
search
download
upload
rename
delete
share
quota

No fake:

account
quota
file list
connected state

Use secure token storage.

Never embed private OAuth secrets.

Handle:

cancel
expired token
revoked account
network failure
missing configuration

============================================================
PHASE 27 — LICENSE / PRO
============================================================

Audit license system.

Never trust:

editable localStorage
client-generated authority
hardcoded PRO
fake tokens
fake signatures
fake ACTIVE state

Use:

trusted server-side verification

or:

Google Play Billing

where applicable.

Separate states:

ACTIVE
EXPIRED
REVOKED
UNVERIFIED
SERVER_UNAVAILABLE
DEVICE_MISMATCH

A network timeout must NOT automatically mean revoked.

A malformed token must NOT become PRO/ACTIVE.

============================================================
PHASE 28 — LICENSE SERVER SECURITY
============================================================

Do not allow arbitrary user-provided license server URLs to receive sensitive tokens.

Use trusted endpoint allowlist/configuration.

Never send authorization tokens to an untrusted origin.

Minimize device telemetry.

Document:

what data is collected
why
retention
privacy behavior

============================================================
PHASE 29 — OTA
============================================================

Real flow:

check
↓
version compare
↓
asset validation
↓
real download
↓
real progress
↓
file verification
↓
integrity verification
↓
Android installer

Never mark:

100%

until actual download completion.

Never claim:

Update Installed

until actual installer result is known.

Never silently install arbitrary APKs.

============================================================
PHASE 30 — TASKS / CLOUD RUNNERS
============================================================

Audit:

tasks.js
settings.js
Cloud Worker
Colab
Vercel
Railway
Docker
Cloudflare

Separate:

CORE APK
from
OPTIONAL EXTERNAL SERVICE

No hidden remote worker dependency.

If service is required:

show explicit configuration and failure state.

Do not expose server secrets.

Remove dead runner controls from normal user settings.

============================================================
PHASE 31 — BROWSER
============================================================

Fix:

back
forward
reload
loading
file chooser
downloads
cookies
external links

Use safe WebView configuration.

No SSL bypass.

No unsafe JS bridge.

No arbitrary filesystem access.

============================================================
PHASE 32 — GALLERY / LOCAL MEDIA
============================================================

Audit local gallery.

Do not recursively scan the entire Downloads tree on every refresh if avoidable.

Prefer:

MediaStore
URI-based indexing
thumbnail caching
incremental updates

Metadata:

real values only.

Unknown:

--

Do not label unknown videos:

1080p
HD

without evidence.

============================================================
PHASE 33 — DATABASE / STORAGE
============================================================

Audit:

Room
DataStore
MediaStore
FileProvider

Fix:

migrations
orphan records
duplicate records
stale file references
missing files
corrupt preferences

Do not use destructive migration blindly.

Ensure upgrade preserves user data.

============================================================
PHASE 34 — LIFECYCLE
============================================================

Test:

Activity recreation
rotation
background
foreground
process death
service restart
reboot

No:

duplicate service
duplicate notification
memory leak
stale coroutine
released-player crash

============================================================
PHASE 35 — PERFORMANCE
============================================================

Profile:

cold start
warm start
tab switching
search
streaming
download
gallery
browser
VPN
Drive

Move expensive work off main thread.

Use:

Coroutines
Dispatchers.IO
Dispatchers.Default
WorkManager

where appropriate.

Avoid:

Thread.sleep
blocking network
large readBytes()
huge bitmap allocation
full DOM rebuilds
unnecessary polling

============================================================
PHASE 36 — LOOP / TIMER / RETRY AUDIT
============================================================

Search:

while
for
setInterval
setTimeout
recursive functions
retry
reconnect

Every repeating path must be:

bounded
cancelable
lifecycle-aware

Review:

18+ pagination
recommendation loops
download polling
search refresh
auto-heal
socket reconnect
timers
event cascades

No infinite synchronous recursion.

No uncontrolled retry.

No background timer after feature/tab is gone.

============================================================
PHASE 37 — MEMORY
============================================================

Audit:

WebView
Media3
downloads
gallery thumbnails
Room
torrent
VPN
listeners
coroutines

Ensure cleanup.

Large files:

stream to disk.

Never load full large media into memory.

============================================================
PHASE 38 — NOTIFICATIONS
============================================================

Audit:

download
VPN
media

Use:

correct channels
correct importance
correct permissions
real progress
correct actions

No duplicate/stale notifications.

============================================================
PHASE 39 — ANDROID COMPATIBILITY
============================================================

Test supported Android versions.

Verify:

storage
permissions
notifications
foreground service rules
WebView
PiP
VPN
native libraries

Do not claim ABI support without actual native binaries.

============================================================
PHASE 40 — ABI
============================================================

Inspect native libraries.

Determine actual support:

arm64-v8a
armeabi-v7a
x86_64

Set ABI filters accordingly.

Do NOT simply add ABIs.

============================================================
PHASE 41 — GRADLE / DEPENDENCIES
============================================================

Audit:

Gradle
AGP
Kotlin
Java
compileSdk
targetSdk
minSdk
dependencies

Remove unused dependencies.

Resolve conflicts.

Do not break native library compatibility.

============================================================
PHASE 42 — R8 / RELEASE
============================================================

Review R8/ProGuard.

Protect reflection/JNI-dependent:

Media3
Room
Capacitor plugins
V2Ray/Xray
serializers

Do not suppress all warnings blindly.

Test minified release APK.

============================================================
PHASE 43 — ADMIN APK
============================================================

Create a SEPARATE Admin APK from the existing admin functionality.

Do NOT copy the complete User APK.

Admin APK should contain only:

Admin Login
Dashboard
Users
Devices
Licenses
Subscriptions
App Versions
OTA
Content/Source Management
System Settings
Audit Logs
Notifications

Admin authentication:

secure
server-authoritative
role-based

Never hardcode admin password.

Never store private Admin API secret in APK.

Admin APK:

→ trusted Admin API

User APK:

→ user services / legitimate APIs

============================================================
PHASE 44 — ADMIN SECURITY
============================================================

Require:

admin authentication
session expiry
token refresh
logout
role checking
server-side authorization

Every admin API endpoint must independently verify permissions.

Do not trust:

UI hidden buttons
client role
localStorage role
APK-only authorization

Server must authorize.

============================================================
PHASE 45 — ADMIN AUDIT LOG
============================================================

Record administrative actions where appropriate:

who
what
when
target
result

Do not log:

passwords
private access tokens
sensitive user content unnecessarily

============================================================
PHASE 46 — FULL UI/UX PASS
============================================================

Audit:

Movies
Downloads
18+
Gallery
Browser
Drive
Tasks
Media
Settings
Admin APK

Fix:

blank screen
dead button
overflow
clipping
keyboard overlap
status bar
navigation bar
small touch targets
long filenames
long titles
landscape
portrait
tablet

Every screen:

Loading
Success
Empty
Error
Retry

No infinite spinner.

============================================================
PHASE 47 — ACCESSIBILITY
============================================================

Add:

content descriptions
proper labels
usable touch targets
font scaling
contrast
focus behavior

Do not rely on color alone to communicate state.

============================================================
PHASE 48 — FINAL SECURITY SCAN
============================================================

Search final repository for:

localhost
127.0.0.1
/api/
server.js
mock
fake
dummy
Math.random()
password
clientSecret
refreshToken
accessToken
SslErrorHandler
proceed()
cleartext
mixedContent
debugging
MANAGE_EXTERNAL_STORAGE
GET_ACCOUNTS

Every match must have a documented reason.

No accidental secrets.

No unsafe SSL bypass.

No fake state.

============================================================
PHASE 49 — AUTOMATED TESTS
============================================================

Create/fix tests for:

URL validation
download state machine
Room
DataStore
recommendation ranking
completion ratio
negative feedback
VPN parser
native bridge
player lifecycle
settings persistence
license states

============================================================
PHASE 50 — FULL DEVICE TEST
============================================================

Test RELEASE APK on a clean Android environment.

Test:

cold start
restart
background
foreground
rotation
offline
slow network
Wi-Fi
mobile data

Downloads:

start
pause
resume
retry
cancel
delete
open
share
large file
network loss
recovery
process death

Streaming:

start
buffering
resume
subtitle
PiP
fullscreen
network switch

VPN:

connect
disconnect
reconnect
invalid profile
network switch
service restart

Drive:

login
logout
refresh
file list
download
restart

18+:

access gate
search
For You
watch
resume
history
Up Next
like
dislike
not interested
source hide
reset

Browser:

navigation
back
forward
reload
download
SSL error

Admin APK:

login
logout
dashboard
user management
device management
license
OTA
audit logs

============================================================
PHASE 51 — NODE-OFF TEST
============================================================

STOP:

Node
npm start
npm run dev
server.js
admin server

Do NOT run backend.

Install User APK.

Verify:

launch
navigation
downloads
streaming
local media
browser
VPN
18+
settings

No runtime Node dependency.

============================================================
PHASE 52 — EXTERNAL SERVICE FAILURE TEST
============================================================

Simulate:

Internet OFF
API unavailable
Drive unavailable
license server timeout
source unavailable
torrent engine failure
disk full

App must:

not crash
not freeze
not fake success

Show:

real error
+
retry

============================================================
PHASE 53 — UPGRADE TEST
============================================================

Install previous version.

Create:

downloads
history
favorites
settings
VPN profiles

Upgrade APK.

Verify:

data preserved
database migrated
files preserved
settings preserved

============================================================
PHASE 54 — FINAL BUILD
============================================================

Read actual package.json scripts.

Run the correct build flow.

Where applicable:

npm ci

npm run build

or the project's actual configured build command.

Then:

npx cap sync android

cd android

./gradlew clean
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease

Do not skip tests.

============================================================
PHASE 55 — FINAL RELEASE CERTIFICATION
============================================================

Verify:

APK exists
APK installs
APK launches
signed correctly
correct applicationId
correct versionCode
correct versionName

Test RELEASE APK.

Do not certify based on DEBUG build alone.

============================================================
PHASE 56 — DOCUMENTATION
============================================================

Create/update:

docs/
architecture.md
security-audit.md
permissions-audit.md
download-report.md
streaming-report.md
adult-recommendation-report.md
drive-report.md
vpn-report.md
torrent-report.md
license-report.md
ota-report.md
admin-apk-report.md
performance-report.md
testing-report.md
release-report.md
known-limitations.md

Every bug record:

BUG
ROOT CAUSE
FILE
FIX
TEST
RESULT

============================================================
PHASE 57 — FINAL QUALITY GATE
============================================================

The project is NOT complete unless:

[ ] User APK launches
[ ] Admin APK launches
[ ] User APK does not require Node runtime
[ ] Admin API remains functional
[ ] no hidden localhost dependency
[ ] no fake runtime state
[ ] no exposed private secret
[ ] no global SSL bypass
[ ] WebView hardened
[ ] permissions minimized
[ ] secure token handling
[ ] Download stable
[ ] Download resume stable
[ ] Download retry stable
[ ] Download delete stable
[ ] Torrent correctly separated
[ ] Movie search stable
[ ] Streaming stable
[ ] Media3 stable
[ ] PiP stable
[ ] Browser stable
[ ] Google Drive stable
[ ] VPN stable
[ ] VLESS validation stable
[ ] 18+ gate stable
[ ] 18+ recommendation stable
[ ] 18+ history stable
[ ] 18+ Continue Watching stable
[ ] 18+ Up Next stable
[ ] recommendation state local by default
[ ] OTA reports real progress
[ ] license verification authoritative
[ ] tasks/cloud dependency explicit
[ ] settings stable
[ ] Room migration stable
[ ] offline mode works
[ ] no uncontrolled loops
[ ] no obvious memory leaks
[ ] no obvious ANR
[ ] release build succeeds
[ ] release APK installed and tested
[ ] Admin APK tested
[ ] documentation updated

============================================================
FINAL ENGINEERING PRINCIPLE
============================================================

DO NOT:

make UI look correct
hide errors
fake results
fake progress
fake speed
fake VPN
fake license
fake Drive
fake metadata
fake OTA
fake download

DO:

use real state
use real error propagation
use real database state
use real native events
use legitimate external APIs
use secure authorization
use modern Android APIs
test the actual release APK

============================================================
FINAL ARCHITECTURE
============================================================

                    ┌─────────────────────┐
                    │     ADMIN WEB       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │     ADMIN API       │
                    │ Trusted Backend      │
                    └──────────┬──────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
                    ▼                     ▼
             ┌──────────────┐     ┌──────────────┐
             │  ADMIN APK   │     │   USER APK   │
             └──────────────┘     └──────┬───────┘
                                         │
                     ┌───────────────────┼──────────────────┐
                     │                   │                  │
                     ▼                   ▼                  ▼
                  Kotlin              Room             DataStore
                  Native
                     │
        ┌────────────┼─────────────┬──────────────┐
        │            │             │              │
        ▼            ▼             ▼              ▼
    Downloads      Media        VPN/Xray       Torrent
        │            │             │              │
        └────────────┴─────────────┴──────────────┘
                     │
                     ▼
             Legitimate HTTPS APIs
              / Google Drive / etc.

USER APK RUNTIME:
NO NODE.JS BACKEND

ADMIN SYSTEM:
SEPARATE TRUSTED BACKEND

============================================================
FINAL INSTRUCTION
============================================================

DO NOT START BY DELETING CODE.

DO NOT START BY REWRITING THE WHOLE PROJECT.

Start by auditing.

Preserve working implementations.

Fix one verified issue at a time.

Build after meaningful changes.

Test after every subsystem.

Review the final diff.

Run complete regression.

Only then certify the APK.

THE FINAL RESULT MUST BE:

STABLE
SECURE
FAST
MAINTAINABLE
HONEST
PRODUCTION-READY
AND ACTUALLY FUNCTIONAL.