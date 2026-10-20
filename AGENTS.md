# Personal Keyboard

Android-first personal keyboard, a separate product from Doudou. This repository is the product contract and executable native interaction prototype, not a completed production keyboard.

- Read spec/product.md, spec/interaction.md and spec/architecture.md before changing behavior. spec/spec.yaml is the visual product map; run npm run spec:build after changing its sources.
- Spec opens as an interactive domain world, not a documentation landing page. Keep domain capabilities, owned objects, selectable relation contracts, product behavior and implementation responsibilities connected. Documents and prompts are details of this world; native APK and clearly labeled web illustrations are separate surfaces.
- The keyboard has one compact candidate/action row. Do not reintroduce permanent Input / Voice / Advisor tabs.
- Advisor flow: shrink → wait for host insets/layout → capture → identify and OCR → generate → expand results. Capture failure stays visible and recoverable; never fabricate a successful capture.
- Real APK demo must display Android output through the Node/scrcpy bridge. Web simulations, deterministic demo responses and real model responses must remain distinguishable.
- Do not start an emulator automatically. Use only an explicitly selected device; emulator resources are bounded and must be stopped after validation. Restore a real phone's prior IME/accessibility settings after temporary validation.
- No raw chat screenshots, account addresses, credentials, personal profiles or device serials in Git. Runtime and screenshots belong under ignored .runtime/.
- Model prices need a dated official source and unit conversion. Personal subscription quota is not an unlimited production API entitlement.
- Local vocabulary, personal style and per-contact relationship memories are separate. Missing final text is not a negative example; skipped reply options are not dislikes.
- Report actual native, browser, device and model verification separately. Never label this demo a full Chinese input engine or real ASR until those are implemented and tested.
