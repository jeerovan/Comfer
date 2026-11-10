
# SYNC DATA

This is a launcher for Android but also has modules (Notes,Tasks,Journals). Requirement is to access modules data on different platforms (iOS and Desktops(macOS,Windows,Linux)). A Flutter based app is required just to sync and manage modules (Notes, Tasks Journals), cross platform, except web.

Data will include:

 - All launcher data including Settings,App Lists, Folder and modules(Notes,Tasks,Journals) for Android Devices
 - Modules data (Notes, Tasks, Journal) for Flutter based app (cross platform: iOS and Desktops)

## Flutter app

Flutter app to sync and manage: Notes,Tasks,Journals.

- on iOS, UI/UX should be similar to Android including themes,colors,thumb reach. a navigation back button at the bottom along with other buttons whenever required.
- for iOS, use mobile-design skill
- on Desktop, use standard UI recommendations
- Desktop does not require thumb reach access. All actions should be at top.

## Backend

Comfer already has a backend which fetches wallpaper assets, at /Volumes/JS/Repos/comferweb/ and it is hosted at https://comfer.jeerovan.com. It already has a local auth to manage wallpapers, uses local/cloud postgres database. Wallpaper service is free, but sync is going to be a paid service (subscription based).
For sync, initialise a new backend at /Volumes/JS/Repos/comfersync/ in node.js which will be hosted at https://comfer.jeero.one

- it will use local postgresql for development and neon.tech postgres via cloudflare hyperdrive
- it will have email-otp based authentication using neon.tech
- cloud will have only encrypted data
- user will have 24 words recovery phrase only to decrypt data locally, no secondary authorised device
- consult repository at /Volumes/JS/Repos/secure_file_vault for authentication, onboarding, encryption, sync, lost recovery phrase handling
- sync is gated by a paid subscription which should appear before device registration
- this project had subscription setup earlier, find in older commits and reimplement
