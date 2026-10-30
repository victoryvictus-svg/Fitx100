# Fit100 — 100-day Android fitness tracker

Native Kotlin Android app with **four tabs**: Today, Diet, Progress, Profile. Designed in forest green, lime and white with a custom launcher icon.

## Features
- Today: 100-day countdown, 7-item daily checklist, workout/rest schedule, exercise checkboxes, 30/60/90-second rest timers.
- Diet: dairy-free meal template inspired by the supplied Fit N Man chart, gym/rest calorie targets and ₹200 daily budget. Nutritional values are estimates and must be verified against portions.
- Progress: weight log, change since 106 kg, photo journal links, daily spending total, weekly-review guidance.
- Profile: weight, calories, protein, water, sleep and spend check-ins, progress-photo picker, adjustable start date, meal and gym reminder hours.
- Local persistence: SharedPreferences and Android document URI permissions. No login or cloud sync.
- Notifications: alarms are scheduled on device, subject to Android permissions, power restrictions and device behavior.

## Build
Push the project contents to a GitHub repository and use **Actions → Build Android APK → Run workflow**. Download the Fit100-debug-apk artifact, extract and install app-debug.apk. Or open in Android Studio.

## Limitations
Version 1.2 has been compiled, packaged, signature-verified and checked in an Android emulator. Physical Pixel and Samsung device testing remains outstanding. Workout timer is in-app (not a background service); exact reminders can be delayed or blocked by device settings. The progress page lists recent weight values rather than drawing a line graph. Photo files are not copied into the app; document URI links can stop working if originals move. This is not a medical or clinical calorie prescription.


## Profile redesign (v2)
Editable personal details, fitness targets, dietary preferences, gym settings, date, check-in, progress-photo picker, reminder times/toggles, privacy and app information. Values are stored locally using SharedPreferences. No cloud sync is implemented.

## Daily motivation photo (v3)
The Today screen now shows the most recent accessible progress photo uploaded through Profile or the home photo card, replacing the placeholder automatically after a new selection. The image is cropped to fit the home card without altering the original. If no photo is linked, an upload prompt appears. Images stay with the Android document provider; if moved or permission is revoked, the card falls back to an earlier accessible image. The photo does not update by itself each day unless a new one is added.

## Adaptive layout and settings
The app now applies system-bar, display-cutout and keyboard insets, uses a scrollable content area capped at 640 dp on wide displays, and reduces side spacing on compact screens. Below 380 dp or with font scaling above 1.15, dense headers and targets stack and dashboard metrics use two columns. Bottom navigation uses larger touch targets.

Profile is now labeled Settings, with expandable groups and daily check-in and progress photos first. Open sections, active tab and scroll position survive activity recreation. Existing preferences are preserved.

Validation: Kotlin source compiled against Android API 35 with Kotlin 2.0.21. Full APK build could not resolve the Android Gradle plugin in the available offline cache. Device visual testing remains outstanding. Check narrow and wide phones, Pixel 10 Pro XL and Galaxy S25 Ultra, portrait/landscape, large fonts, gesture/three-button navigation, and the keyboard while entering a check-in.

## Daily before/after photo catalogue
Open the gallery icon on Today or Settings (also available in Progress). Choose today or an earlier date and add separate Before workout and After workout images using the system photo document picker. Tap a thumbnail to open the full image, or Change photo to replace that slot. Dates remain stable if the journey start date changes. Saved days appear newest first after the selected date, with Show more days after seven entries. Compact displays stack the pair vertically. Earlier progress photos remain accessible without being assigned a before/after label.

Persistent URI access and the pending photo slot survive activity recreation; cancelling the picker leaves the existing image unchanged. Thumbnails load on a background executor at bounded size. Inaccessible images show a choose-again prompt. The Today motivation card includes catalogue images, preferring the after-workout photo for the latest date.

Validation: source compiled with Kotlin 2.0.21 against Android API 35. Physical-device and full APK validation remain outstanding due to the offline build dependency issue above.

## Version 1.1: safe system bars and Back navigation
System insets now apply to a persistent outer container through the Android content container, including status bars, display cutouts, navigation bars and keyboard height. Rendering a page only replaces the inner host. Safe areas use the dark app background with light system icons.

Back from Diet, Progress, Settings or the photo catalogue always returns to Today. On Today, the first Back press shows a prompt; a second within two seconds exits. Navigating, pausing or recreating the activity clears the confirmation. Both legacy Back and Android 13+ Back callbacks use the same controller. The catalogue's onscreen Back button also returns to Today.

Validation: Kotlin/API 35 compilation passed. Standalone BackNavigationCheck covers secondary pages, double presses, expiry, navigation and pause resets. Fit100-v1.1.apk was built offline using the installed Kotlin compiler and Android SDK aapt2, D8, zipalign and apksigner (build/offline-package/build.py). APK signature, package version, SDK levels and Back callback manifest flag were verified. It uses the same debug signing certificate as the previous workspace APK. The standard app/build/outputs/apk/debug/app-debug.apk was updated too. No physical phone or configured emulator was available for visual testing.

Install Fit100-v1.1.apk over the existing app to apply these changes. The APK is version code 2 / version name 1.1.

## Version 1.2: Profile reference redesign
The Profile overview now follows the supplied dark forest/lime reference: portrait or initials, edit action, journey progress, weight summary and a white icon menu. Personal Information, Fitness Goals, Diet Preferences, Workout Settings, Smart Reminders, Progress Photos & Measurements, App Settings and Privacy each open their own screen. Detail screens omit the bottom tab bar to give forms more space. The main tab bar uses a consistent custom outline icon set.

Personal details and targets use outlined fields with explicit Save Changes actions and numeric validation. Drafts survive activity recreation. Goals expose gym/rest calories, protein/carbs/fat and activity level; diet and workout screens use selection chips. Existing preference keys are preserved. Dashboard calorie/protein/budget targets and the default rest timer now read saved values. Meal and exercise lists remain templates rather than automatically generated plans.

Profile photos support the system gallery picker and full-resolution camera output through a private, URI-grant-only portrait provider. The progress section includes the existing daily before/after catalogue plus dated front/side/back photos, body measurements and weight history. Privacy can hide progress previews; originals can still be opened explicitly. App Settings offers CSV export via the system document picker and Android notification settings. Theme/language switching, backup restore, biometric/app lock and custom reminders from the mockup are not implemented.

Reminder times now preserve minutes. A master toggle cancels or schedules reminders; individual times/toggles save immediately. Exact delivery remains subject to Android permissions and device power settings.

Validation: Kotlin compilation and signed APK verification passed. Installed v1.2 on an API 37 emulator at 411 dp, reviewed Profile and Edit Profile screenshots, checked a goals screen at 320 dp with 1.3x text, verified saved personal details in local preferences, and confirmed Back from a detail screen goes home and two further Back presses exit to the launcher. Standalone BackNavigation checks also passed. Fixed the offline packager manifest order and explicitly enabled activity resizing, eliminating the observed aspect-ratio letterboxing. Camera capture, CSV destinations and every phone model were not exhaustively tested.

Install Fit100-v1.2.apk (version code 3) over the existing app. The signing certificate matches previous workspace APKs. Current screenshots are in previews/Profile.png and previews/Edit-Profile.png.

## Version 1.3: green surfaces, daily snapshot and Profile Back navigation
Light surfaces, forms and empty space now use soft green shades with darker green secondary text. Backgrounds fill the entire host, including the unused portion of short pages. The Profile overview has a dark forest gradient; the other pages use a light green gradient.

A Daily Snapshot card below Today's check-in shows saved water, protein and sleep values, saved-entry count, and shortcuts to the daily photo catalogue and Progress. The same card follows the check-in form's Save button.

Back from every Profile detail screen returns to the Profile overview. A catalogue opened from Profile also returns to Profile; catalogues opened from Today or Progress still return to Today. Back from the Profile overview returns to Today. Double-Back exit on Today remains unchanged. Both toolbar buttons and system Back share the same controller.

Validation: Kotlin compilation, expanded standalone navigation checks, APK signing verification, and API 37 emulator checks passed. Checked the new green check-in and snapshot screens, system Back from all seven primary Profile menu destinations, toolbar Back to Profile, Profile Back to Today, and double-Back exit to the launcher. Physical phone testing remains outstanding. The offline packager now derives version metadata directly from Gradle. Fit100-v1.3.apk is version code 4 / version name 1.3.

## Version 1.4: reference-inspired Home / Today
The Home screen now uses a dark forest/lime dashboard with a leaf mark, reminder shortcut, day/date badge, transformation banner, photo actions, calorie/protein/water/task progress, gym/rest summary, daily schedule, water tracker, rest timer and mountain motivation card. Saved progress photos fill the banner; a private profile portrait is the fallback. Without a photo the banner shows an upload prompt. The landscape art and outline icons are native vector drawings. The reference portrait and food photography are not bundled.

Phones use a scrollable vertical layout with two-column metrics. Narrow screens and large text stack the banner. At 840 dp and above with standard text scaling, the schedule/timer and water/motivation sections occupy two columns. Existing safe system-bar/keyboard insets and Profile Back navigation are preserved.

Water logging supports 250 ml, 500 ml, custom 1-5000 ml additions and one-step undo. Data stays in the existing daily litres key, with conversion to millilitres for display. The default target is 4000 ml from the supplied reference and can be edited in the water card or Fitness Goals. The reminder link reflects the existing configured water reminder; it does not invent hourly alarms.

Schedule completion updates the task value, bar and ring immediately. The rest timer supports preset selection, Start/Pause/Restart and ten-second adjustments. Water updates preserve an active timer, and remaining time survives activity recreation. Leaving Home pauses it. Completion uses the declared normal VIBRATE permission where hardware supports vibration. Changing the default rest timer in Workout Settings applies on returning Home.

Validation: Kotlin/API 35 compilation, signed APK verification and standalone hydration/navigation checks passed. API 37 emulator checks covered 411 dp portrait, 320 dp with 1.3x text, wide/landscape layout, persisted schedule completion, 250 ml/500 ml/custom-dialog water saves, undo, a running timer across a water update, and a ten-second timer completing at 00:00 with Restart while the app remained active. Physical Pixel/Samsung testing and every document/photo provider remain unverified. Current screenshots are under previews/.

Install Fit100-v1.4.apk (version code 5 / version name 1.4) over the existing app. It is signed with the same local debug identity as prior workspace builds.


## Version 1.5: reference-inspired Progress dashboard
Progress now includes Overview, Body Measurements, Photos, Analysis and History in the forest/lime theme. Dated weight charts support Week, Month and All Time, with tappable points and weight entry. Measurement forms, same-angle dated photo comparison, daily before/after catalogue links, composition report entry, muscle/fat trends, body ratios, regional report values and CSV export use saved local records. No sample health data or reference portraits are bundled. Missing reports show empty states; scores and regional values require manual report entry and are not inferred from photos. BMI uses saved height and weight; formula source: https://www.cdc.gov/growth-chart-training/hcp/using-bmi/calculating-bmi.html . No diagnostic classifications are assigned.

Progress detail Back returns to Progress overview, then Today; the daily catalogue opened from Progress returns to Progress Photos. Existing Profile navigation and double-Back exit on Today are preserved. System bars remain outside app content. Narrow screens and larger fonts wrap content and allow horizontal tab scrolling.

Validation: native Kotlin compilation, APK signature verification, ProgressRecords and BackNavigation checks passed. API 37 emulator checks covered saving a weight, persisted records, multi-point chart rendering, measurements, composition score rendering, detail Back navigation, and 320 dp width with 1.3x fonts. Screenshots in previews/Progress.png, Body-Measurements.png, Weight-Progress.png and Body-Composition.png contain emulator-only sample records. Physical Pixel/Samsung testing and every photo/document provider remain unverified.

Install Fit100-v1.5.apk (version code 6 / version name 1.5) over the existing installation. It uses the same local debug signing identity as previous workspace versions.


## Version 1.6: Diet and meal planning
Diet now uses forest backgrounds, green surfaces and lime actions, with Meal Plan, Recipes, Grocery List and Diet Settings. Six dated meal slots open ingredients, preparation steps and estimated per-serving macros. Recipes can be saved as favourites or assigned as dated replacements. Eaten marks can be undone; completed slots retain the selected recipe when preferences later change. Recipe ingredient filters use saved food/dairy preferences and comma-separated exclusions. Food illustrations are native drawings; reference photography is not bundled.

Customisation saves Gym Day/Rest Day/Custom calorie targets, protein/carbs/fats, food budget, food preferences, exclusions, preferred-food notes and six meal timings. Preferences and existing Profile fields share storage. Targets do not automatically resize recipe portions. Week view opens seven dates with the repeating template and dated overrides. Grocery lists use selected-date recipe quantities, persistent checkmarks and Android share chooser; duplicate ingredients show recipe multiples. No shopping-cart integration is included.

Analytics displays actual saved calorie/protein check-ins over seven days and estimates macros from meals marked eaten. Eaten-meal estimates stay separate from manual check-in totals to prevent double counting. Nutrition is an illustrative estimate and is labelled accordingly. The small recipe library is not an automatic target/budget optimiser; meal count remains six, preferred foods are notes, and no cooking-time/cuisine generator or custom grocery editor is provided. Existing legacy meal-completion flags remain stored; new six-slot completion uses separate keys.

Validation: Kotlin/API 35 compilation and APK signature verification passed. Recipe tests cover slot coverage, vegan/vegetarian/eggetarian filtering, multiple case-insensitive exclusions and valid recipe data. API 37 emulator checks covered meal completion/undo, persisted replacement, target edit/save (140 to 145 g protein), grocery/settings navigation, detail Back to Meal Plan and Home, and 320 dp with 1.3x fonts. A cold-boot System UI ANR was dismissed before testing; no Fit100 crash was recorded. Physical Pixel/Samsung and sharing-provider testing remain outstanding. Screenshots are under previews/Diet.png, Meal-Details.png, Grocery-List.png and Customize-Meal-Plan.png. Emulator test data is not bundled in the APK.

Install Fit100-v1.6.apk (version code 7 / version name 1.6) over the existing app; signing identity matches earlier workspace APKs.


## v1.0 release candidate (version code 8)
The challenge starts at the timestamp when Start My 100-Day Challenge is tapped, runs for 100 full days and refreshes consistently across Today/Profile/Progress. The old fixed default date has been removed. Camera capture, on-demand Camera permission and Android Settings recovery now apply to all photo slots. Gallery imports are copied into private storage. The new Daily Photo Album groups saved before/after/body photos and older numbered images, with filters, pagination and in-app viewing. Real measurements start empty for new users.

Both Fit100-v1.0.apk (release certificate) and Fit100-v1.0-upgrade.apk (previous certificate, data-preserving upgrade) disable debugging. Read RELEASE-v1.0.md for signing-key backup, installation differences, validation and remaining physical-device release gates. Neither APK has been published. Use tools/build_apk.py for reproducible local offline packaging.


## v1.0 sports update (version code 9)
A dedicated Sports tab supports 50+ activities and Other / Custom, with a persistent activity timer, manual dated session logs, duration, optional distance/notes, a seven-day summary and activity history. These are manual/timer records; GPS, automatic steps, wearables and specialised scoring are not included. Sports logs are included in the existing CSV export. Start Challenge is now prominent at the top of Home and on Profile. Date-only preferences no longer initiate a challenge; already saved challenge timestamps remain intact. Recording activities never starts the challenge.

Validation: Kotlin compilation, release/upgrade APK signing and standalone sports timer checks passed. API 37 emulator checks saved a 30-minute/2.5-km activity, confirmed history and weekly summary, verified the timer continues across closing/reopening, checked Back to Home with the challenge still unstarted, and reviewed 320 dp / 1.3x text navigation. A wrapped navigation label was reduced to a single line. Physical-device validation remains outstanding. Screenshots: previews/Sports.png, Sports-History.png and Challenge-Start.png. Install Fit100-v1.0-upgrade.apk over an existing workspace build; new installations may use the separately release-signed Fit100-v1.0.apk.

## Sports reference redesign (version code 10)
Sports now opens a native forest-green/lime workout dashboard inspired by the supplied reference. It includes a dated workout summary, actual seven-day session marks, four selectable plans, a searchable/category-filtered 13-exercise library, editable daily exercise selection, instructions, elapsed workout timing, optional weight and rep/second set logs, a 60-second rest countdown, saved activity history and dated recovery checklists. Existing 50+ sports, custom activity, manual logging, activity timers and deletion remain available under Track a Sport / Activity. System Back from Sports detail pages returns to the workout dashboard.

Artwork is an original native barbell illustration; the reference's exercise photographs are not bundled. Exercise instructions beyond bench press and plank are labelled general training cues. Steps, heart rate and calorie burn are not simulated. Workout and rest timestamps, set records and selections persist locally. Workout duration is saved to the existing sports history on explicit completion. The challenge is not started by logging workouts.

Validation: Kotlin/API 35 compilation and signed APK verification passed. No emulator or physical device was connected for this update, so visual layout and touch interaction validation remain outstanding. Install the version-code-10 Fit100-v1.0-upgrade.apk over the prior workspace installation to preserve its data.
