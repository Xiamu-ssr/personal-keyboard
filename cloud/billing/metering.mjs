// Meter successful final ASR text once, independent of transport and provider units.
export function billableCharacters(text) {
  if (typeof text !== "string") throw new TypeError("Final ASR text required");
  return Array.from(text.normalize("NFC")).filter((c) => !/\s/u.test(c)).length;
}
