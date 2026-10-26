# Nintendo Switch 2 shortcut

A module card that switches the TV straight to the HDMI input a games console sits on, through the TV's own input
app (`TvContract.buildChannelUriForPassthroughInput`), the way the launcher's Inputs menu does.

It picks the input in this order: the one remembered from last time; the one whose HDMI-CEC name or custom label
says "Switch" or "Nintendo"; else it asks once with a list of the HDMI inputs and remembers the answer (clear the
app's data to choose again). No permissions.

## Build

`./gradlew testReleaseUnitTest assembleRelease` in this folder (JDK 17, Android SDK 34).

The card's picture is two drawn controller halves. For your own picture, put a 640x360 `banner.png` in
`app/src/main/res/drawable-nodpi/` and delete `res/drawable/banner.xml`; keep copyrighted artwork out of a public
repository.
