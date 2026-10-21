# 豆豆输入法

Android-first personal keyboard, a separate product from Doudou. This repository contains the visual product contract and a native Android personal-use alpha. Paid ASR, real text-model accounts, automatic model memory extraction and Doudou sync are not configured.

- Read spec/product.md, spec/interaction.md and spec/architecture.md before changing behavior. spec/spec.yaml is the visual product map; run npm run spec:build after changing its sources.
- Spec opens as an interactive domain world, not a documentation landing page. Keep domain capabilities, owned objects, selectable relation contracts, product behavior and implementation responsibilities connected. Documents and prompts are details of this world; native APK and clearly labeled web illustrations are separate surfaces.
- The keyboard has one compact candidate/action row. Do not reintroduce permanent Input / Voice / Advisor tabs.
- Keyboard and settings are surfaces of one input-method App. Feedback is an underlying learning mechanism, not a fourth user mode. Vocabulary/style and voice reinforce each other; people/events are shared memory for typing, voice and advisor, with explicit subject and evidence scope.
- Advisor flow: shrink → wait for host insets/layout → capture → identify and OCR → generate → expand results. Capture failure stays visible and recoverable; never fabricate a successful capture.
- Public Spec provides native screenshots, actual APK recordings and downloads. Online Android trial is cancelled; do not add an emulator host or device bridge unless the user requests it again. Public pages must never start or connect to the user's Mac or ADB.
- Local device demos display real Android output through Node/scrcpy and require an explicitly selected device. Keep resources bounded and stop after validation; restore real-phone IME/accessibility settings after temporary validation. Keep screenshots, web simulations and real model responses distinguishable.
- No raw chat screenshots, account addresses, credentials, personal profiles or device serials in Git. Runtime and screenshots belong under ignored .runtime/.
- Model prices need a dated official source and unit conversion. Personal subscription quota is not an unlimited production API entitlement.
- Local vocabulary, personal style and per-contact relationship memories are separate. Missing final text is not a negative example; skipped reply options are not dislikes.
- Report actual native, browser, device and model verification separately. Never label this demo a full Chinese input engine or real ASR until those are implemented and tested.
