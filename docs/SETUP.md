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

### Trying the M1 learning loop
1. **Home → New subject**, enter a goal, then tap **Next**. Answer the 2 or 3 questions (or skip any), then tap **Build my course**.
2. The **outline editor** opens. Tap any chapter or topic to rename, move or delete it, or use **Add topic**. Then tap **Start course**.
3. Tap **Continue** to open the first topic. The lesson is written the first time you open it, which takes a few seconds, then it's saved on the phone.
4. Swipe the explanation cards, try the practice question, answer the check questions, then tap **Finish topic**.
5. When every topic in a chapter is done, tap **Take the chapter quiz**. You need 80% to pass. Every answer is checked by code and labelled **Exact**.
6. **You** shows your streak, mastery and reviews due. The gear icon opens Settings.


### Trying the M2 practicals
1. Pass a chapter quiz. The subject map now shows a **Practical** row, and the floating button says **Do the practical**.
2. The AI writes a task suited to the course: Python, JavaScript, SQL, a calculation, or a written answer. Code tasks are tested before you see them, because the AI's own solution must pass its hidden tests.
3. Type your answer and tap **Run tests** (or **Run query**, **Check answer**, **Submit for grading**). The first Python run starts Python on your phone, which can take a few seconds.
4. To get a different kind of task, tap **⋮ → Change type**. To get a fresh task of the same kind, tap **⋮ → New task**.
5. **Gate check:** on a code task, run a wrong answer first and confirm it fails, then a right answer and confirm it passes and the chapter shows **Finished**.

Chapters you finished before this update stay finished; their practical shows as optional.


### Trying M3: longer lessons, "I don't get it", videos and simulations
1. **New lessons are longer** (5 to 8 cards, with a "Common mistakes" and a "Recap" card). For a lesson written before this update, open the topic and tap **⋮ → Rewrite lesson**. This uses one AI request and clears that page's answers.
2. **"I don't get it"** sits under the lesson cards. Each screen tries one way of explaining (simpler words, an analogy, fill in the blanks, a video, a simulation, step-by-step questions, teach-back, a basics check). Answer the question under it: a right answer means it helped, and next time you're stuck the app starts with that way. **Show me another way** moves to the next one.
3. **Better analogies:** open **You → ⚙ Settings → About you**, fill in your work and what you already know, and tap **Save**.
4. **Watch a short video:** the chip under "Why it matters". Without a YouTube key it opens a YouTube search; with one, a short video plays inside the app. Tap **Find another** if the video isn't useful.
5. **Simulations** (physics, chemistry, biology and maths topics) come from PhET and need internet. Tap **Open full screen** for a bigger view.

### Optional: a YouTube key, so videos play inside the app
1. Go to **console.cloud.google.com** and sign in with your Google account.
2. At the top, click the project picker. Choose the project AI Studio created for your Gemini key (often named "Gemini API" or "Default Gemini Project"), or click **New project**, name it, and click **Create**.
3. In the search bar, type **YouTube Data API v3**, open it, and click **Enable**.
4. In the left menu, open **APIs & Services → Credentials**, then click **+ Create credentials → API key**. Copy the key.
5. Recommended: click the new key, and under **API restrictions** choose **Restrict key → YouTube Data API v3**, then click **Save**.
6. In the app, open **You → ⚙ Settings → YouTube key**, paste the key, and tap **Save**.

The free quota is 10,000 units a day, and each video search costs 100, so about 100 searches a day. The app searches once per topic and remembers the video.

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
| "Gemini is busy right now" | Google's servers are overloaded. The app already retried twice. Wait a minute, or pick a **flash-lite** model. |
| "Gemini took too long to answer" | Try again, or pick a faster **flash** or **flash-lite** model. |
| "The code runner stopped responding" | Tap the button again; the runner restarts itself. |
| "Couldn't ask Google for Drive access" or error 10 | The Android client's package name or SHA-1 doesn't match. Repeat M4 step 3 with the SHA-1 from **signingReport**. |
| "Access blocked: app has not completed verification" | Add your Gmail under **Audience → Test users** (M4 step 2.4). |
| Phone not listed in Studio | Re-plug the cable, check that the USB debugging popup was accepted, try another cable or port. |

## M4: Google Drive backup (one-time Google Cloud setup)

Drive backup needs Google to know your app. This is a one-time setup of about 10 minutes, in the same Google Cloud project as your keys. **Export file / Import file** works without any of it.

### 1. Turn on the Drive API
1. Open **console.cloud.google.com** and pick your project at the top (the one your Gemini key belongs to).
2. In the search bar, type **Google Drive API**, open it, and click **Enable**.

### 2. Set up the consent screen
1. In the left menu, open **Google Auth Platform** (on older consoles: **APIs & Services → OAuth consent screen**). Click **Get started** if asked.
2. **App information:** app name "Learn Anything", and your email as the support email. Click **Next**.
3. **Audience:** choose **External**, then click **Next**. Enter your email as the contact, agree, and click **Create**.
4. Open **Audience**. Under **Test users**, click **+ Add users**, add your own Gmail address and click **Save**. While the app is in "Testing", only test users can sign in, which is right for personal use.
5. Open **Data access**, click **Add or remove scopes**, search for **drive.appdata**, tick `.../auth/drive.appdata` ("See, create, and delete its own configuration data in your Google Drive"), click **Update**, then **Save**.

### 3. Tell Google about the app on your computer (Android client)
1. Find your debug signing fingerprint. In Android Studio, open the **Gradle** panel (the elephant icon on the right), then **LearnAnything → app → Tasks → android → signingReport**, and double-click it. Or, in the **Terminal** tab, run `./gradlew :app:signingReport`.
2. In the output, find **Variant: debug** and copy the **SHA1** line (it looks like `AB:CD:…`).
3. Back in the console, open **Google Auth Platform → Clients → + Create client**.
4. Set **Application type** to **Android**, **Package name** to `com.example.learnanything`, and paste the SHA-1. Click **Create**.

Every phone you install on from this computer uses the same debug fingerprint. A different computer has a different one, so add another Android client for its SHA-1. If you later rename the package, create a new Android client too.

### 4. Optional: account picker sign-in (web client)
Without this step, Google's own Drive permission screen lets you pick the account, which is enough. If you also want the standard "Sign in with Google" sheet:
1. Open **Clients → + Create client → Web application**, name it, and click **Create**.
2. Copy the **Client ID** (it ends in `.apps.googleusercontent.com`).
3. In Android Studio, open `local.properties` in the project root and add a line: `google.webClientId=PASTE_CLIENT_ID`. This file is never committed.
4. Click **File → Sync Project with Gradle Files**, then **Run ▶**.

### 5. Turn backup on
1. In the app, open **You → ⚙ Settings → Backup** and tap **Turn on Drive backup**.
2. Pick your Google account and allow access. Google warns that the app isn't verified, which is expected while it's in Testing: tap **Continue**.
3. The first backup runs straight away. After that the app backs up daily on Wi-Fi, and a couple of minutes after you finish a chapter. **Back up now** runs one immediately.

Backups go to a hidden app folder in your Drive. You won't see them in the Drive app, and the app can't see your other files. The newest 5 are kept. API keys, model files and the AI cache are never included.

### M4 gate: restore on a second phone
1. On phone 1: **Settings → Backup → Back up now**.
2. Install the app on phone 2 from the same computer (plug it in, pick it in the device menu, then click **Run ▶**).
3. On phone 2: **Settings → Backup → Turn on Drive backup** with the same Google account. Then tap **Restore from Drive**, pick the newest backup, and tap **Replace**.
4. Check that phone 2 shows the same courses, finished topics, quiz and practical results, and the same streak and mastery on **You**.

**No setup? Use a file instead:** on phone 1 tap **Export file** and save it to Drive, Downloads or anywhere else. On phone 2 tap **Import file**, choose it, and pick **Replace**.

**Merge or Replace?** Merge keeps both phones' progress (for each record, the newest change wins, and deletions carry over). Replace makes the phone match the backup exactly.
