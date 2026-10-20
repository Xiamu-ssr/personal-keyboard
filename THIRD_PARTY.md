# Third-party components

- The Android viewer is [NetrisTV/ws-scrcpy](https://github.com/NetrisTV/ws-scrcpy), MIT, pinned in `demo/third-party.lock.json`. Its build preserves its LICENSE and the bundled scrcpy Apache-2.0 notice. Source is fetched into ignored `.runtime/`; it is not republished as original project code.
- The viewer is built for Android only. The two scoped adaptations bind HTTP to loopback and restrict automatic device tracking to `PERSONAL_KEYBOARD_DEVICE`; `demo/setup.mjs` checks the pinned source before applying them.
- `marked` and `yaml` are MIT dependencies pinned by package-lock.json, used only to build the Spec.
- Competitor images are research references, not project assets. No third-party screenshot is bundled in the repository.
- Rime/Fcitx5, OCR and model providers are candidates documented in the Spec. Their code/models have not been bundled into this prototype; evaluate each component's license before integrating.
