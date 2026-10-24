# 0.7.2 native keyboard comparison

Scope: the held-space dictation interaction, tools and return to editing. This is an invited development build, not complete Doubao parity or a human dictation accuracy study.

## Visual evidence

- Source: official Doubao Android 1.5.2 on the authorized old phone; `.runtime/v072/reference/11–17-*.png`. Official download: https://srf.doubao.com/h5 . No claim of watching a complete official usage video.
- Implementation: APK `d4dbd28d6a6c675583d65e5832ecdcbdf34a6e7db6e0f8eceaa53e6e046b34aa`, `.runtime/v072/reference/49–56-*.png`. Earlier 4d0f captures 31–42 and 46–48 preserve their original version labels (excluding invalid captures). The final build changes only private-mode tool availability from 4d0f.
- Both phone captures are 1080 × 2412 pixels at density 480, dark theme, the same owned practice Activity. Native Android has no CSS viewport. Density maps to 360 × 804 dp; system/navigation settings were unchanged. Different practice text is deliberate to test prior-draft ownership.
- Full native states were opened and inspected. `.runtime/v072/comparison-final5.png` puts the source and final implementation in the same image, using equal lower-screen crops (1080 × 1212 pixels, downsampled to 540 × 606). This focused comparison makes target edges, captions and colors readable. Original full captures remain unmodified.
- Actual final-APK recordings: `.runtime/v072/gestures-raw.mp4` and `advisor-raw.mp4`. Public clips omit launcher/bootstrap and subsequent separate test cases; each displayed task retains its original frame order and speed. The emulator microphone clip is silent and demonstrates interactions only.

## Comparison history

1. Initial comparison was blocked by three P2 differences: a 20 dp taller keyboard, flat listening background and clipped action edges. Shared surface height is now 264 dp; padding belongs to letter keys; the recording Drawable reaches the window edges.
2. The second comparison still showed a flat background. The reusable Paint retained alpha from the neutral arc. Resetting its color before the base shader fixes redraws; target type was reduced to 17 sp.
3. The final same-phone comparison verifies height, full-bleed edges, blue-gray listening surface and white/red withdrawal feedback. Minor differences in exact arc curvature, caption placement and waveform geometry remain P3.
4. Independent review of the earlier f482 build retained two P2 issues: no discoverable ordinary-touch hands-free route and a waiting surface blocking edits. The tools grid now has an explicit hands-free card. Release immediately restores letter keys with a progress row; the first edit releases the provisional composing span. Raw ASR history can finish without replacing the user's correction. The 68cf review confirmed these fixes and found a P1: direct punctuation bypassed the edit handoff. The 4d0f build moves the handoff into the shared character-command entry, covering letters, digits and punctuation; direct-punctuation phone validation preserves the complete provisional phrase and the appended punctuation. Independent code-path review closed the P1. A later P2 (the hands-free card looked available in private mode) is fixed with disabled/opacity state, verified in native controls and phone capture 54. The final d4db incremental review closes this P2 and accepts the observed ordinary-touch hands-free completion path. No P0/P1/P2 remains open within this review's scope.

## Fidelity surfaces

- Typography: native system Chinese/Latin type; readable 17 sp action labels and 13 sp feedback. No bitmap labels, cropped text or duplicate labels in the inspected final states.
- Layout: typing, tools and recording preserve the same outer height. Recording removes irrelevant keys; ordinary short-space still inserts a space. Two-by-two tools have a single clear return control. The platform navigation area is separate from app content.
- Colors: dark keys and panels, blue/cyan real-level waveform, blue-gray listening background; withdrawal uses white with red text, newline uses white with dark text. Base-gradient alpha and edge clipping are fixed.
- Assets: actual native views and waveform; official Material Symbols Rounded vectors with Apache license bundled in the APK. No copied Doubao logo or synthetic product screenshots.
- Copy: actions describe outcomes (撤回输入 / 换行 / 免按说话 / 完成). The same 帮我回 label is used in the practice instructions and keyboard. No generic “取消显示” action.

## Interaction evidence and limits

- Final APK: 28 JVM/JNI tests pass; native suite has 23 passes, 5 explicit opt-in/fixture skips, no failures. Lint has zero errors and 30 warnings.
- Separate real-service checks pass for advisor selection/insertion, held gestures and editing while a real silent ASR task reaches a terminal state. These are not recognition-quality measurements.
- Old-phone microphone checks show real interim text, withdrawal retaining `draft:`, final insertion and visible-key editing. The earlier 4d0f build, with optional rewriting enabled, shows a rewriting progress row while keys remain available; the edited comma survives completion. The final d4db build also completes an ordinary-touch hands-free microphone task via its visible Done button (50–53). The subsequent status query already saw succeeded jobs, so it does not establish a precise RPC race timestamp. The real Android editor test separately rejects late replacements after ownership is released.
- Only owned practice content was used. The test account is deleted; phone preferences, microphone permission, IME selection and enabled IMEs are restored. The isolated emulator is stopped and its ports are clear. Source screenshots and raw recordings are private ignored evidence, not release assets.
- Hands-free is discoverable through tools, rather than Doubao's persistent tap-to-speak header. Complete one-handed comfort, human continuous dictation, every host App and full screen-reader flows remain unverified.

## Handoff

final result: passed

Limited to the observed held-space, return-to-editing, tools, privacy-state and hands-free tasks. The independent reviewer inspected the actual final d4db screenshots and unchanged character handoff, and preserved earlier evidence under its original build labels. The report is `.runtime/v072/independent-p2-recheck.md`. No claim of complete product acceptance, exact RPC race timing, human continuous dictation accuracy or every-host compatibility follows from this result.
