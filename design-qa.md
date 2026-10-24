# 0.7.3 native keyboard comparison

Scope: curved held-space gestures, advisor boundary and earlier-chat capture, first-use guidance, and keyboard clipboard/input-history. This is an invited development build, not complete Doubao parity or an ASR accuracy study.

## Evidence

- Source: official Doubao Android 1.5.2 on the authorized old phone, `.runtime/v072/reference/12–14-doubao-dark-*.png`; official download https://srf.doubao.com/h5 . Existing source captures remain labeled with their original version.
- Implementation: final APK `0d6d11e98c509f9911e0a556e9f691745911400929cb478e43d73019a26d4679`; old-phone interaction evidence is candidate `78cfdcd1dd49f87decdc565fed877306af74d09f812fa31a62f078eb7b358353`, before the query and immutable capture-metadata corrections described below. `.runtime/v073/phone/31–42-*.png`, `60–62-*.png`, plus emulator `final/clipboard.png`, `final/keyboard-history.png` and `final/onboarding*.png`.
- Source and final phone recordings are 1080 × 2412 pixels, density 480, dark theme, same owned practice Activity. Native viewport is 360 × 804 dp, with original device navigation/display settings. Practice text differs intentionally to test draft ownership.
- `.runtime/v073/comparison-final.png` combines source and implementation in one input: identical lower-screen crops `(0,1200,1080,2412)`, normalized to 540 × 606 pixels. Full captures and readable target-region comparison were opened and inspected.
- Final APK emulator recordings are `.runtime/v073/gestures-raw.mp4` and `advisor-raw.mp4`. Published clips preserve contiguous task ranges, frame order and playback speed; no generated UI. The keyboard clip is silent. Advisor includes real Cloud/Aliyun inference wait.

## Findings and iteration

1. P2: rotating rectangular labels did not reproduce the curved interaction. `ArcAction` now draws a curved ribbon and a curved text baseline. The initial canvas text alignment stacked Chinese glyphs and clipped the right label; that capture is explicitly rejected. The 78cfd labels use a measured starting offset and left-aligned glyph drawing. New emulator and same-phone source comparison show complete readable labels in normal, withdrawal and newline states.
2. P2 candidate: the lower visible ribbon extended past the neutral-release boundary. Ribbon layout now occupies 0.37–0.64 of surface height, before the 0.68 neutral boundary. Final physical edge/neutral captures supplement normal target tests. This is not a study of all hand sizes or one-handed comfort.
3. P2: advisor looked glued to the keyboard. Its entry is now a bordered pill with spacing above keys; results have a distinct outlined rounded sheet and handle. Actual phone `01-idle.png`, `61-advisor-result.png` and `62-advisor-two-pages.png` show separation and readable replies without text overlap.
4. Earlier capture/retry defect: supplement failure could regenerate from old evidence. Capture/model failures now have separate retry actions; an injected unavailable screenshot service after the actual older-page gesture verified retry without repeating the scroll. The fixture was subsequently rerun on the final APK; it is not a protected-window test.
5. Independent source review found F1 private clips imported after reopening, F2 old reply image indices shifted, F3 raw/withdrawn entries offered as normal input history, F4 advisor guide ignored login. The final source blocks delayed private clip import, remaps old-group sources, lists only adopted nonwithdrawn results, and checks both login and capture readiness. Native controls test these paths; the final live advisor also checks the restored old group's source image.
6. P2 from independent review: limiting all history before filtering could hide older adopted results. `HistoryStore.reusable` applies adoption filters before SQL LIMIT. A native SQLite regression keeps an earlier adopted result visible after 60 newer raw/withdrawn rows. Raw history and backup retain their previous scope.
7. P2 from independent review: cancel could mutate the screenshot-order flag between validation and saving. Each actual capture now freezes `prepend` before dispatch and uses that immutable value for duplicate detection, local persistence, reply remap and session images. This closes the field-reread path; a deterministic pause at the exact cancel/save boundary was not injected.
8. First-use permanent mic denial now routes to this App's system settings. A separate final-APK fixture verifies that route with ActivityMonitor; actual manual permission grant in the system Settings UI was not tested in this release.

## Required fidelity surfaces

- Typography: native system Chinese/Latin, 17 sp curved labels and 13 sp feedback; final same-phone labels are complete. Tool/history labels wrap appropriately. No glyph labels or bitmap text substitute for native controls.
- Layout: typing/tools/held voice share the same height; recording removes letter keys, release restores them. Tools remain a 2 × 2 grid. Clipboard/history use a scrollable in-IME library and fill the original editor. Advisor entry and sheet have a visible boundary, and the bottom system navigation belongs to the platform.
- Colors: dark neutral keyboard and sheets, blue-gray listening background, cyan/blue waveform; white/red withdrawal and white/dark newline feedback. Exact Doubao band thickness, waveform shape and caption wording differ intentionally (P3).
- Assets: actual native controls and sensor-level waveform, licensed Material Symbols Rounded vectors. No copied brand logo, synthetic product screenshots or screenshot compositing in the App.
- Copy/content: action labels describe the outcome. Earlier-page action states its successful-generation cost. First-use guide allows basic typing without optional permissions. Clipboard states local-only retention (30 text clips / 7 days). No private conversation or clipboard content was used.

## Verification and limits

- Final build: 32 JVM/JNI passes; native suite 28 passes and 7 explicit opt-in/fixture skips; lint 0 errors / 32 warnings. Node has 19 passes / 2 PostgreSQL integration skips.
- Final live advisor: initial one-tap, two chronologically ordered screenshots, restored old group and original-image remap, reply insertion without sending. Captured sample area 1991 / 2400 = 82.96%; this measures the owned practice screen, not every host app.
- Old phone on 78cfd (final 0d6d upgrade installed separately): real interim microphone text, withdrawal restoring `draft:`, final transcription editable in place; one-tap advisor and older-screen generation. Audio source is local TTS through room audio, not natural human speech accuracy.
- Duplicate-page detection is a coarse pixel guard. Dynamic avatars/loading content and unusual host scroll behavior are not semantically proven. Three-screen model behavior, all host apps, full screen-reader flow and natural human dictation remain outside this acceptance scope.
- Cancel stops the advisor operation; already captured screenshots remain local when history is enabled and can be deleted. No promise of zero retained evidence after cancel. The exact cancel/write race was not benchmarked.
- Candidate-path evidence retains its source APK hash. The d665 query correction filters adopted keyboard-history rows before SQL LIMIT; a real SQLite regression proves that 60 newer raw/withdrawn rows do not hide the older valid result. Voice/capture rendering and normal-path behavior are unchanged from 78cfd. The final 0d6d snapshot correction freezes screenshot ordering metadata. Neither change relabels old phone captures as final-APK device runs.

## Handoff

final result: passed

The independent reviewer closed the confirmed issues within the observed/source scope, including the adopted-history query and immutable capture ordering corrections. The final APK hash and native/live logs were independently bound in `.runtime/v073/independent-final-review.md`. No confirmed P0/P1/P2 remains in that scope; this is not full-product acceptance. Two disposable QA accounts were deleted; the isolated emulator and its ports are stopped; original phone preferences, IME and permission state were restored, and the final APK was installed. Receipt: `.runtime/v073/cleanup.json`.
