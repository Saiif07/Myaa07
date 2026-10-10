# MYRAA (Android + backend)

Build the APK
  * GitHub:  push this folder -> Actions -> "Build APK" -> download artifact `myraa-debug-apk`
  * Local:   ./gradlew :app:assembleDebug      (Windows: gradlew.bat :app:assembleDebug)
             gradlew downloads Gradle 8.7 by itself on first run. The CI also uploads the official wrapper jar
             (artifact `gradle-wrapper-files`) - commit it if you want the standard wrapper.

First run on the phone: WAKE ON -> SETUP (4 steps: overlay, battery, contacts, accessibility).

Voice examples
  "Hey Myraa, open YouTube" / "flipkart kholo" / "open Flipkart on Chrome"
  "myntra me shoes search karo"            (searches INSIDE the Myntra app)
  "rahul ko whatsapp par message bhejo ki main late hoon"   (asks 'haan' / 'cancel' first)
  "send message to mom on telegram saying call me"

Backend (optional, full multi-agent): python3 myraa.py serve
