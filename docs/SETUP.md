# Setup: from zero to the app on your phone

Three parts: (1) get a free Gemini API key, (2) open the project in Android Studio,
(3) run it on your phone over USB. Allow about 30 minutes the first time, most of it downloads.

---

## 1. Get a free Gemini API key (Google AI Studio)

1. On your computer, open **https://aistudio.google.com** and sign in with your Google account.
2. If asked, accept the terms of service.
3. Click **Get API key**. It's in the left sidebar, or at the bottom left on narrow windows.
4. Click **Create API key**.
   - If it asks for a Google Cloud project, choose **Create API key in new project**
     (or pick an existing project). The free tier doesn't need billing turned on.
5. Copy the key. It's a long string, usually starting with `AIza`.
6. Treat it like a password. **Don't** put it in any file in this project or send it in chat.
   You'll paste it straight into the app on your phone (step 3.9). The app encrypts it with a key
   held in the phone's secure hardware.

> Free tier notes: limits change without notice and are shown in AI Studio, under the API key
> or usage page. If you hit one, the app tells you how long to wait. That's normal, not a crash.
> On the free tier Google may use what you send to improve its products. Check the current
> terms in AI Studio.

---

## 2. Open the project in Android Studio

### 2a. Install Android Studio (skip if you already have a current version)
1. Download it from **https://developer.android.com/studio** and install it with the defaults.
2. On first launch, choose **Standard** setup and let it download the Android SDK. This takes a while.
3. This project uses Android Gradle Plugin 9.5, so you need a **recent** Android Studio.
   If Studio later says the plugin is too new, go to **Help → Check for Updates**
   (on macOS, **Android Studio → Check for Updates**) and update.

### 2b. Get the code
1. On the Welcome screen, click **Clone Repository**. If a project is already open, use
   **File → New → Project from Version Control**.
2. URL: `https://github.com/ashhartrails/Learn-Practice-Improve.git`. Choose a folder, then click **Clone**.
3. Switch to the M0 branch: click the branch name in the top toolbar (or the bottom-right status bar),
   go to **Remote branches → origin/claude/keen-bohr-tuhx2n → Checkout**.
   (Once the pull request is merged you can stay on `main` instead.)
4. If asked **Trust Project?**, click **Trust Project**.

### 2c. Let Gradle sync
1. Studio starts **Gradle sync** on its own, and a progress bar appears at the bottom.
   The first sync downloads Gradle 9.9.1 and every library. Wait until it says **Sync finished**.
2. If it asks to install a missing SDK platform (**Android 37**) or build tools, click the
   **Install …** link in the error and accept the licence.
   Manual route: **Tools → SDK Manager → SDK Platforms**, tick **Android 37.0**, click **Apply**.
3. If sync complains about the JDK: **Settings → Build, Execution, Deployment → Build Tools →
   Gradle → Gradle JDK**, then choose the bundled **jbr-21** (any JDK 17 or newer works).
4. You don't need to create `local.properties`. Studio writes it for you with your SDK path, and Git ignores it.

---

## 3. Run it on your phone over USB

### 3a. Turn on Developer options and USB debugging (one-time, on the phone)
Menu names vary a little by brand. These are for Pixel and stock Android; on Samsung it's
**Settings → About phone → Software information**.
1. **Settings → About phone**, then tap **Build number** 7 times. Enter your PIN if asked.
   You'll see "You are now a developer!".
2. **Settings → System → Developer options** (Samsung: **Settings → Developer options**).
3. Turn on **USB debugging**.

### 3b. Connect and trust the computer
1. Plug the phone into the computer with a USB cable that carries data, not just charge.
2. On the phone, if it asks what the USB connection is for, choose **File transfer**.
3. A popup on the phone says **Allow USB debugging?** Tick **Always allow from this computer**
   and tap **Allow**.
4. In Android Studio's top toolbar, the device menu should now show your phone's name.
   - Windows only: if it doesn't appear, you may need your phone maker's USB driver
     (Samsung, for example, has its own). Google's driver is under **Tools → SDK Manager →
     SDK Tools → Google USB Driver**.

### 3c. Run the app
1. In the toolbar, make sure the run configuration says **app** and your phone is selected.
2. Click the green **Run ▶** button (or press Shift+F10, or Ctrl+R on macOS).
3. The first build takes a few minutes. Then the app opens on your phone, called **Learn Anything**.
4. Tap **You** (bottom right). That's the Settings screen.
5. Paste your Gemini key into **Paste key** and tap **Save and test**.
   You should see "Key works. N models available." The **Model** card shows **Automatic**
   and the model it picked. Tap **Change** to choose a different one.
6. Tap **Home → New subject**, type a goal (e.g. `SQL joins`), choose a level and weekly
   hours, and tap **Build my course**. A progress line shows characters arriving.
7. When it finishes, the course opens: chapters and topics. Tap any topic to see its objectives.
8. **Gate check:** swipe the app away, reopen it, and the course is still there under Home and Library.
   That shows the outline was generated, validated and saved on the phone.

### Wireless option (Android 11+, same Wi-Fi network)
**Developer options → Wireless debugging → On**. Then in Android Studio, open the device menu,
choose **Pair Devices Using Wi-Fi**, and scan the QR code from the phone's **Wireless debugging → Pair device with QR code**.

---

## Command line (optional)

Run these from the project folder. On Windows use `gradlew.bat` instead of `./gradlew`.

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew installDebug
```

The debug APK ends up in `app/build/outputs/apk/debug/app-debug.apk`.

## If something goes wrong
| Symptom | What to do |
| --- | --- |
| "Google rejected this key" | Copy the key again from AI Studio. Make sure nothing is cut off and there are no extra spaces. |
| "You've hit Gemini's free-tier limit" | Wait the number of seconds shown, or pick another model under **You → Model → Change**. |
| "The AI's course didn't pass our checks twice" | The model sent malformed output twice in a row. Tap **Build my course** again, or try another model. |
| Error mentioning 404 or "model" | The chosen model was retired. Go to **You → Model → Change → Automatic**. |
| Phone not listed in Studio | Re-plug the cable, check that the USB debugging popup was accepted, try another cable or port. |
