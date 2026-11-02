# Come Clone (WebView app)

Original "Come" APK ek single-screen WebView wrapper tha (splash -> website). Ye uska clean, open-source Kotlin version hai.

## Kya karta hai
- Splash screen (logo pulse + loader) jab tak site load na ho
- `START_URL` wali site WebView me, baaki sab links Chrome Custom Tab / external app me (tel:, upi:, whatsapp: sab chalte hain)
- Site ka "download app" top banner hide karne ki JS (`app/src/main/assets/hide-download-banner.js`)
- Network error par 3 baar retry (backoff), phir browser me fallback
- Back button WebView history me peeche jaata hai

## Settings badalna (`gradle.properties`)
| Key | Matlab |
|---|---|
| `START_URL` | Jo website kholni hai |
| `APP_NAME` | Phone me dikhne wala naam |
| `APP_ID` | Unique package id |

Logo/icon: `res/drawable/come_logo.xml` aur `res/drawable/ic_launcher_foreground.xml` (apna logo yahin lagao).

## GitHub par build
1. Naya repo banao, ye saari files upload/push karo (branch `main`).
2. **Actions** tab -> *Build APK* khud chalega (ya *Run workflow*).
3. Run khatam hone par **Artifacts -> Come-clone-apk** se APK download karo.

## Play Store ke liye signing
Abhi release APK debug key se sign hota hai (install testing ke liye theek). Play Store ke liye `app/build.gradle.kts` me apna `signingConfigs` add karo aur keystore ko GitHub Secrets me rakho. Keystore kabhi repo me commit mat karo.

## Dhyan do
- `START_URL` me default wahi link hai jo original APK me tha (`seo_channel` tracking tag ke saath). Us tag se traffic original channel ko attribute hota hai; apna link/tag lagao.
- Ye app kisi third-party site ko wrap karta hai. Site ki terms, aur agar real-money gaming/fantasy site hai to Play Store policy + India ke state-wise gaming rules check kar lo.
