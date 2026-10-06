# LessGram — Instagram without the endless feeds

This build is a prototype of the WebView approach discussed in chat.

## What it does
- Opens Instagram inside LessGram rather than launching the separate Instagram app.
- Uses Instagram's own web login/session; LessGram does not ask for an Instagram password in its own backend.
- Home and Reels tabs are blocked by design.
- DM opens Instagram Direct.
- Search opens Instagram search.
- Profile opens Instagram.
- Obvious Explore/Reels URL navigation is blocked outside the DM context.

## Important limitations
Instagram may restrict or change behavior in embedded WebViews, and the web UI changes over time. This is not an official Instagram client and is not a guarantee of access to personal-account DMs.

The Android notification permission/channel is prepared, but this prototype does NOT claim that Instagram's private push notifications are forwarded into LessGram. Reliable background DM push requires a supported notification integration.

Do not use a password you cannot afford to lose while testing. If Instagram shows an unsupported-browser/login warning, stop and report the exact message; do not try to bypass security controls.
