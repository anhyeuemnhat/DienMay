# Windows visual/peripheral acceptance (NOT RUN here)

Record Windows edition/build, JDK/runtime, display dimensions, DPI scale,
monitor count, font settings, graphics driver and tester/date. Use an isolated
SQL test deployment, not the real shop database.

Run the following matrix independently: 1366x768 and 1920x1080, each at Windows
scaling 100%, 125%, 150%. Log out/restart the application after a DPI change when
required by Windows. Linux -Dsun.java2d.uiScale is NOT a substitute for this gate.

For each role, inspect login, home, every accessible main view, dialogs, POS,
receiving/transfers, product/customer/warranty/report tables and receipt windows.
Check work-area maximize state (taskbar still usable), no exclusive full-screen,
all action buttons/text accessible, scrollbar behavior, keyboard tab/Enter,
combo selections, Vietnamese glyphs, row selection after sorting/filtering,
window switching, close/logout while busy and recovery after connection loss.

Invoke `ant ui` on the actual display for automated checks but also manually read
long labels and verify dialogs at 150%: outer button bounds alone cannot prove
text is fully legible. Keep screenshots per resolution AND DPI to avoid overwrites.
The supplied UiSmoke fixture tests presentation without SQL; execute real workflows
separately for functional acceptance.

Test the physical barcode/HID scanner as keyboard input including leading zeroes,
focus, terminator and fast scans. Test actual printer selection, page breaks,
Vietnamese receipt output and reprinting a persisted invoice. Cancel print dialogs
and verify no sale is replayed. Validate protected user-profile ACLs for pending
business data and ensure no token/password is stored there.

Original NetBeans Designer round-trip needs the missing original .form/source pairs.
It cannot be accepted by opening these handwritten views in an editor.
