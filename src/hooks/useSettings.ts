import { useState, useEffect, useCallback } from "react";

const STORAGE_KEY_TZ = "f1sp-timezone";
const STORAGE_KEY_THEME = "f1sp-theme";

function getInitialTimezone(): string {
  try {
    return localStorage.getItem(STORAGE_KEY_TZ) ?? Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return "Atlantic/Reykjavik";
  }
}

export function useSettings() {
  const [timezone, setTimezoneState] = useState(getInitialTimezone);
  const [theme, setThemeState] = useState(
    () => localStorage.getItem(STORAGE_KEY_THEME) ?? "default"
  );

  const setTimezone = useCallback((tz: string) => {
    setTimezoneState(tz);
    localStorage.setItem(STORAGE_KEY_TZ, tz);
  }, []);

  const setTheme = useCallback((t: string) => {
    setThemeState(t);
    localStorage.setItem(STORAGE_KEY_THEME, t);
  }, []);

  // The useState initialiser already reads from localStorage or falls back to
  // Intl.DateTimeFormat(). This effect persists that detected value back to
  // localStorage on first visit so subsequent visits restore it correctly.
  useEffect(() => {
    if (!localStorage.getItem(STORAGE_KEY_TZ)) {
      try {
        const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
        setTimezone(detected);
      } catch { /* use default */ }
    }
  }, [setTimezone]);

  return { timezone, setTimezone, theme, setTheme };
}
