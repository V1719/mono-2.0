# Mono 2 — a customizable minimalist launcher

Alphabetical text list, favourites on home, a drag-to-scrub A–Z wave on the edge,
notification previews, Now Playing, next calendar event — and a full **Customize**
screen so it can look and feel the way you want.

## Updating the build you already have on GitHub

1. In your GitHub repo, open the folder that holds the old project (the one with the
   `app` folder in it) → the **⋯** menu at the top right → **Delete directory** → Commit.
   (Leave `.github/workflows/build.yml` alone. Stale old files WILL break the build, so
   don't skip this.)
2. **Add file → Upload files**, drag the new `MonoLauncher` folder in again, Commit.
3. **Actions** tab → wait for the green check → download **Mono-debug-apk**.
4. **First time only:** uninstall the old Mono from your phone before installing this one.
   (This version is signed with a fixed key; the previous one wasn't, so Android refuses
   to update over it. From now on every new build installs straight over the last one
   and keeps your favourites and settings.)

## Customizing

Long-press an empty part of the home screen (or open All apps → ⋮) → **Customize**.

- **Theme**: presets (Wallpaper dark/light, AMOLED, Dark, Light, Midnight, Forest, Sepia),
  wallpaper or solid background, wallpaper dimming, text colour, accent colour
  (from your wallpaper or fixed), font.
- **Home screen**: favourites position (bottom/middle/top) and alignment, text size, row
  spacing, clock on/off/size/format, date, notification previews, Now Playing, calendar line.
- **App list and scrolling**: list text size, alphabet on the left or right, wave strength,
  **scroll speed**, haptic ticks.

To make it feel like another launcher you're comparing against, put them side by side and
tune: favourites text size, row spacing, the font, wallpaper dimming, wave strength and
scroll speed.

## Using it

- Swipe up = all apps · swipe down from the top = notification shade.
- **Drag along the A–Z strip** to scrub through the list.
- Long-press any app: add to home, move up/down, hide, app info, uninstall.
- Long-press the "Enable notifications" hint to dismiss it.

## Known limits

- Notification previews and Now Playing need *Notification access* (Customize → Manage).
  If the switch is greyed out: Settings → Apps → Mono → ⋮ → *Allow restricted settings*.
- Swipe-down-for-notifications uses a hidden Android call; a future Android version could block it.
- No icon packs, widgets, or per-favourite pop-up shortcuts yet.
- Written without being able to run it on a device — if something misbehaves, tell me exactly what.

## Toolchain
AGP 9.2.0 (built-in Kotlin) · Gradle 9.4.1 · compileSdk/targetSdk 36 · minSdk 28.
