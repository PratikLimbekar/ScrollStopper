# ScrollStopper

An Android service app that uses AccessibilityService to detect when you're doomscrolling, and eventually do something about it. The project explores how an app can observe system UI events, 
identify which application is currently being interacted with, and use that information to detect potentially excessive scrolling behavior.

The idea is simple:

**You open Instagram → you start scrolling → ScrollStopper notices → eventually, it intervenes.**

Currently in progress. But does what is needed from my side.
---

## Current Features

* **Accessibility event monitoring**

  * Listens to Android accessibility events.
  * Identifies the package/application generating events.
  * Logs accessibility event types for experimentation and debugging.

* **Application detection**

  * Can determine which application is currently generating accessibility events.
  * Provides the foundation for identifying distracting/social-media applications.

* **Doomscrolling detection groundwork**

  * Built specifically to investigate how scrolling behavior can be detected through Android's accessibility framework.
  * Hits the back button if it detects reels being seen.

---

Built in Kotlin using the Android Studio and AccessibilityService

## 📂 Project Structure

```text
ScrollStopper/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── ...
│           │
│           ├── res/
│           │   └── ...
│           │
│           └── AndroidManifest.xml
│
├── Data/
│
├── gradle/
│   └── wrapper/
│
├── scrollstopper.apk
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew.bat
```

---
### Prerequisites

You'll need:

* Android Studio
* Android SDK
* An Android device or emulator
* USB debugging enabled if using a physical device

### Clone the repository

```bash
git clone https://github.com/PratikLimbekar/ScrollStopper.git
cd ScrollStopper
```

Open the project in **Android Studio** and let Gradle sync.

### Run the application

Connect an Android device or start an emulator, then run the `app` configuration from Android Studio.

---

You may also install the APK file provided in the repository to use the application. You must enable the Android Accessibility Service from your devices' settings for it
to work as intended. Steps to do that may vary from device to device, but a basic plan is as follows:

## Enabling the Accessibility Service

ScrollStopper relies on an Android Accessibility Service, so the service must be enabled manually.

On your Android device:

```text
Settings
   ↓
Accessibility
   ↓
Installed Apps / Downloaded Apps
   ↓
ScrollStopper
   ↓
Enable
```

The exact location of the setting may vary depending on the Android manufacturer.

Once enabled, the service can begin receiving accessibility events from the system.

---

## 💡 Why Build This?

Most screen-time tools tell you **how much** time you spent on an application.

ScrollStopper is exploring a slightly different question:

> **Can we detect what you're actually doing inside the application?**

Opening Instagram for two minutes to reply to a message is very different from spending forty minutes continuously scrolling.

The goal of this project is to explore whether Android's accessibility framework can provide enough information to distinguish between these behaviors and use that information to help interrupt the latter.
Currently it works on Instagram Application only, and that too on Version 451.0.0 on Android.

---

ScrollStopper uses Android's Accessibility Service API, which provides access to information about interactions with other applications.
As the application runs completely offline and its code being available publicly, privacy of the user is maintained. Hell, it is the reason I made this app in the first place.

## 👨‍💻 Author

Pratik Limbekar

* GitHub: [@PratikLimbekar](https://github.com/PratikLimbekar)
* LinkedIn: [Pratik Limbekar](https://www.linkedin.com/in/pratik-limbekar/)

---

If you find the idea interesting, feel free to explore the code or experiment with the Accessibility Service yourself.
