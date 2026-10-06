# LessGram — easiest APK testing method

## 1. Create a GitHub repository
Create a new empty repository on GitHub, for example `LessGram`.

## 2. Upload this project
Upload all files from this folder to the repository. Make sure `.github/workflows/build-apk.yml`
is included.

## 3. Build the APK
Open the repository on GitHub:
Actions → Build LessGram APK → Run workflow.

The workflow will build a debug APK automatically.

## 4. Download it
After the workflow finishes:
Actions → the completed run → Artifacts → LessGram-debug-apk.

Download and extract the artifact. The `.apk` can then be installed on an Android phone.

## Important
This is a development build. Instagram compatibility, login behavior, DM notifications,
and the Reel-blocking behavior still need real-device testing. Do not use an account you
cannot afford to lose access to while testing.
