import { useState, useEffect, useCallback } from "react";

const STORAGE_KEY_TZ = "f1sp-timezone";
const STORAGE_KEY_THEME = "f1sp-theme";

function getInitialTimezone(): string {
  try {
    return localStorage.getItem(STORAGE_KEY_TZ) ?? Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return "UTC";
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

  // Detect user's timezone on first visit
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
