## Partisan-Telegram messenger for Android

[![Crowdin](https://badges.crowdin.net/p-telegram-android/localized.svg)](https://crowdin.com/project/p-telegram-android)
[![Bitcoin donation](https://img.shields.io/badge/donate-bitcoin-fe9515.svg)](https://telegra.ph/Partisan-Telegram--P-SMS-support-01-02-2)
[![Ethereum donation](https://img.shields.io/badge/donate-ethereum-536bc3.svg)](https://telegra.ph/Partisan-Telegram--P-SMS-support-01-02-2)
[![USDT donation](https://img.shields.io/badge/donate-USDT-26A17B.svg)](https://telegra.ph/Partisan-Telegram--P-SMS-support-01-02-2)
[![Monero donation](https://img.shields.io/badge/donate-monero-f26822.svg)](https://telegra.ph/Partisan-Telegram--P-SMS-support-01-02-2)
[![Litecoin donation](https://img.shields.io/badge/donate-Litecoin-345d9d.svg)](https://telegra.ph/Partisan-Telegram--P-SMS-support-01-02-2)

![](https://github.com/wrwrabbit/Partisan-Telegram-Android/blob/wiki_images/wiki_images/Readme.jpg)

A special version of Telegram that protects peaceful protesters in Belarus (can be used in other countries with authoritarian regimes as well). 
P-Telegram has two passcodes instead of one - the real passcode and a false passcode.
If a user enters the false passcode a series of pre-defined actions is performed such as:
- Sending a custom SOS message to family and a trusted contact using Telegram
- Delete chats and channels that can be used against the user
- Log-out of the account on this device
- Delete all other sessions besides the current one
- And others

Usage of P-Telegram can be used against the user in Belarus to justify torture and jail, therefore P-Telegram looks and feels like the original Telegram as much as possible.

Stay safe.

## Translations

If you'd like to add translations to Partisan Telegram, please join the project on [Crowdin](https://crowdin.com/project/p-telegram-android).

## Contact

Partisan Telegram is developed and maintained by [Cyber Partisans](https://t.me/cpartisans_security). If you have questions about the application, you can ask them in our [bot](https://t.me/partisan_telegram_bot).

## Compilation Guide

**Note**: In order to support [reproducible builds](https://core.telegram.org/reproducible-builds), this repo contains dummy release.keystore,  google-services.json and filled variables inside BuildVars.java. Before publishing your own APKs please make sure to replace all these files with your own.

You will require Android Studio 2025.1.4, Android NDK 27.2.12479018 and Android SDK 35.

1. Clone the Telegram source code with its submodules:
   ```bash
   git clone --recursive --shallow-submodules https://github.com/wrwrabbit/Partisan-Telegram-Android.git Telegram
   ```
   In case you forgot the `--recursive` flag, change to the `Telegram` directory and run:
   ```bash
   git submodule init && git submodule update --init --recursive --depth=1
   ```
2. Copy your release.keystore into TMessagesProj/config
3. Fill out RELEASE_KEY_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD in gradle.properties to access your release.keystore
4.  Go to https://console.firebase.google.com/, create two android apps with application IDs org.telegram.messenger and org.telegram.messenger.beta, turn on firebase messaging and download google-services.json, which should be copied to the same folder as TMessagesProj.
5. Open the project in the Studio (note that it should be opened, NOT imported).
6. Fill out values in TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java – there’s a link for each of the variables showing where and which data to obtain.
7. You are ready to compile Telegram.

