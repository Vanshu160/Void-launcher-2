# Touch controls

Void Launcher's on-screen controls follow the behaviour of MojoLauncher's custom control system. This note records
what was studied, what was reproduced, and what was left out.

## Source and licence

- Studied: `MojoLauncher/MojoLauncher` at commit `510e12ee6825c77f6db61b8d3025841ef7b6c1e9`, under
  `app_mojo/src/main/java/git/artdeell/mojo/input/` (`ControlLayout`, `ControlData`, `ControlJoystickData`,
  `CustomControls`, `LayoutConverter`, `LayoutSanitizer`, `buttons/ControlButton`, `buttons/ControlJoystick`,
  `buttons/ControlInterface`, `LayoutBitmaps`) and `game/TouchEventProcessor.java`.
- Licence of the studied code: GNU LGPL v3 (the repository's `LICENSE`). Void Launcher is GPL-3.0. LGPL-3.0 code can
  be combined into a GPL-3.0 program, so reuse would be allowed with the LGPL notices kept.
- **No code was copied.** The Kotlin here is written from the observed behaviour. The Mojo joystick uses the
  `io.github.controlwear` virtual-joystick library, and the Mojo layout format uses Gson and a zip container for
  bitmaps. Neither is used here. If Mojo code is ever copied in later, that file needs its LGPL-3.0 header and an
  entry in `THIRD_PARTY.md`.

## What was reproduced

| Mojo behaviour | Void Launcher |
| --- | --- |
| Buttons carry up to four key or mouse bindings | `TouchButton.bindings`, up to 4 (`TouchLayouts.MAX_BINDINGS`) |
| Key, mouse and scroll bindings | `Binding.Key`, `Binding.Mouse`, `Binding.ScrollUp`, `Binding.ScrollDown` |
| Toggle buttons switch on at the first lift and off at the next | `TouchButton.toggle`, handled in `TouchRouter.release` |
| Swipeable buttons press the next swipe button a finger reaches | `TouchButton.swipe`, handled in `TouchRouter.moveOnButton` |
| Joystick sends W/A/S/D, diagonals hold two keys | `TouchGeometry.joystickKeys`, `TouchRouter.moveJoystick` |
| Joystick absolute mode: the base moves to the touch | `TouchButton.absolute` |
| Joystick forward lock: pulling past the edge holds Ctrl (sprint) | `TouchButton.forwardLock`, `TouchRouter.updateForwardLock` |
| Buttons anchored to the nearest edge, so they keep their place when the screen changes shape | `TouchButton.anchorRight`, `anchorBottom`, `TouchGeometry.anchorFor` |
| Per-button size, opacity, fill colour, outline colour and corner radius | `TouchButton.size`, `opacity`, `background`, `stroke`, `cornerRadius` |
| Shown in the world, in menus, or both | `TouchButton.inWorld`, `inMenu` |
| Layout saved as JSON, validated and sanitised on load | `TouchLayoutJson` (strict envelope, forgiving buttons, clamped numbers) |
| Editor opened from the in-game gear menu, drawn over the game in place: select, drag, add, clone, delete, properties, save | `InGameLayoutEditor`, `TouchButtonInspector`, `GameSurfaceOverlay` |
| Named layouts: Default Control Layout (built in, restorable), new, use, delete | `TouchControls.saveNamed`, `addLayout`, `setActive`, `ManageLayoutsScreen` |
| Import and export of layout files | Storage Access Framework (`CreateDocument`, `OpenDocument`) in `ManageLayoutsScreen` |
| Routing paused while an editor is open | `TouchRouter.setSuspended` |

## Default layout

`TouchLayouts.mojoDefault()` keeps Mojo's default arrangement for the mouse buttons (primary and secondary), the
chat, inventory, drop and function-key buttons, the toggle for sneak (Mojo's default uses Shift as a toggle), and Esc
in menus. Mojo's four movement buttons are replaced by one joystick on the left, because the earlier request for this
branch asked for a joystick there. Sprint and jump are included as well.

## Input safety

- Each held key or button has an owner: a button id, a joystick id, the camera fingers, or a tap. `HeldCodes` counts
  owners, so a code held by two owners is pressed once and released once.
- `TouchRouter.cancelAll()` runs on surface destruction, on app pause (`MainActivity.onPause`), and at exit. It
  releases everything.
- A context change (world to menu, or the reverse) releases held inputs and clears toggles and forward locks.
- Only one finger can hold a given button. A second finger on a held button is ignored, so it cannot start a camera
  gesture.

## Not built

- The keyboard button (opening the on-screen keyboard), the GUI toggle that hides the controls, and the virtual mouse
  mode. Mojo has all three.
- Mouse passthrough: forwarding a button's touch to the camera as well. Mojo has it per button.
- The drawer (a group of buttons that folds out) and multiple named layouts. Void Launcher keeps one layout per screen
  shape.
- Gamepad input, which Mojo also handles.

## Still needs a phone

The routing and the layout data are covered by unit tests in CI. Only a device can show the rest: how the joystick
feels, whether Minecraft accepts each synthetic key and button event, multi-touch under load, slide-over timing, and
whether layouts survive a restart and a rotation.
