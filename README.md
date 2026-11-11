# ShackLink PTT bridge (Sonim XP-series)

Turns the Sonim PTT key into stock Mumla's own talk command, so Mumla transmits in the
background while another app (ShackLink) stays on screen. Mumla itself is unchanged.

Install the APK, open it once, then: Sonim Settings > Programmable Keys > Select PTT Key App > ShackLink PTT.
Mumla must be connected and set to Push To Talk.

## Floating PTT button (phones without a PTT key, e.g. Z Fold)

Open ShackLink PTT and tap **Show floating PTT button** (allow "Display over other apps" the first time).
A round PTT button floats over every app: drag it by the grip on top, hold the round button to talk.
It sends the same Mumla talk command as the Sonim key, with the same 3-minute safety limit.
Hide it from the app or from its notification.

## CTR2-MIDI PTT jack (USB MIDI)

While the floating button is shown, the app also listens to USB MIDI controllers. A PTT switch in
the CTR2-MIDI's 3.5 mm key jack sends note 20 (press = on, release = off); that keys Mumla exactly
like holding the floating button, and the button turns red while you talk. Unplugging the controller
while keyed un-keys at once, and the same 3-minute limit applies. ShackLink in Chrome keeps using the
controller's knobs at the same time.

## Updating

Open ShackLink PTT and tap **Check for update**. It downloads the latest build from this repo's
release and opens Android's own "update this app?" prompt; the first time Android asks to allow
"Install unknown apps" for ShackLink PTT. Every build is signed with the same key (`app/shacklink.jks`),
so updates install over the existing app and keep its settings.
