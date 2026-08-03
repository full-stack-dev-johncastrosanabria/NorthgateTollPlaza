/**
 * The Angular unit-test builder runs specs under jsdom in Node, and that
 * environment does not expose localStorage — not on window, not globally.
 * AuthService persists the session there, so specs that exercise it need a
 * stand-in. Browsers provide the real thing, so this is test-only scaffolding.
 */
if (typeof globalThis.localStorage === 'undefined') {
  const entries = new Map<string, string>();

  const storage: Storage = {
    get length(): number {
      return entries.size;
    },
    clear: (): void => {
      entries.clear();
    },
    getItem: (key: string): string | null => entries.get(key) ?? null,
    key: (index: number): string | null => [...entries.keys()][index] ?? null,
    removeItem: (key: string): void => {
      entries.delete(key);
    },
    setItem: (key: string, value: string): void => {
      entries.set(key, String(value));
    },
  };

  Object.defineProperty(globalThis, 'localStorage', { value: storage, configurable: true });
  if (typeof window !== 'undefined') {
    Object.defineProperty(window, 'localStorage', { value: storage, configurable: true });
  }
}
