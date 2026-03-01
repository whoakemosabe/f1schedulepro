import { useState, useEffect, useCallback } from "react";
import schedule2026 from "../data/schedule2026";

const STORAGE_ENABLED = "f1sp-notifications-enabled";
const STORAGE_BEFORE = "f1sp-notify-before";
const STORAGE_NOTIFIED = "f1sp-notified-sessions";

export interface NotificationSettings {
  enabled: boolean;
  setEnabled: (v: boolean) => void;
  minutesBefore: number;
  setMinutesBefore: (m: number) => void;
  permission: NotificationPermission;
  requestPermission: () => Promise<void>;
  supported: boolean;
}

export function useNotifications(): NotificationSettings {
  const supported = typeof window !== "undefined" && "Notification" in window;

  const [permission, setPermission] = useState<NotificationPermission>(
    supported ? Notification.permission : "denied"
  );
  const [enabled, setEnabledState] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_ENABLED) === "true";
    } catch {
      return false;
    }
  });
  const [minutesBefore, setMinutesBeforeState] = useState(() => {
    try {
      return parseInt(localStorage.getItem(STORAGE_BEFORE) ?? "30", 10);
    } catch {
      return 30;
    }
  });

  const setEnabled = useCallback((v: boolean) => {
    setEnabledState(v);
    localStorage.setItem(STORAGE_ENABLED, String(v));
  }, []);

  const setMinutesBefore = useCallback((m: number) => {
    setMinutesBeforeState(m);
    localStorage.setItem(STORAGE_BEFORE, String(m));
  }, []);

  const requestPermission = useCallback(async () => {
    if (!supported) return;
    const result = await Notification.requestPermission();
    setPermission(result);
    if (result === "granted") {
      setEnabled(true);
    }
  }, [supported, setEnabled]);

  // Notification check interval
  useEffect(() => {
    if (!enabled || permission !== "granted") return;

    function getNotifiedSet(): Set<string> {
      try {
        return new Set(JSON.parse(localStorage.getItem(STORAGE_NOTIFIED) ?? "[]"));
      } catch {
        return new Set();
      }
    }

    function markNotified(key: string) {
      const set = getNotifiedSet();
      set.add(key);
      const arr = [...set].slice(-100);
      localStorage.setItem(STORAGE_NOTIFIED, JSON.stringify(arr));
    }

    function fireNotification(raceName: string, sessionName: string, minsLeft: number) {
      if ("serviceWorker" in navigator && navigator.serviceWorker.controller) {
        navigator.serviceWorker.ready.then((reg) => {
          reg.showNotification("F1 Schedule Pro", {
            body: `${raceName} — ${sessionName} starts in ${minsLeft} minutes`,
            icon: "/pwa-192x192.png",
            badge: "/pwa-192x192.png",
            tag: `session-${raceName}-${sessionName}`,
          });
        });
      } else {
        new Notification("F1 Schedule Pro", {
          body: `${raceName} — ${sessionName} starts in ${minsLeft} minutes`,
          icon: "/pwa-192x192.png",
        });
      }
    }

    function checkSessions() {
      const now = Date.now();
      const windowMs = minutesBefore * 60 * 1000;
      const notified = getNotifiedSet();

      for (const race of schedule2026) {
        for (const session of race.sessions) {
          const sessionTime = new Date(session.dateUtc).getTime();
          const diff = sessionTime - now;
          const key = `${race.round}-${session.name}`;

          if (diff > 0 && diff <= windowMs && !notified.has(key)) {
            markNotified(key);
            const minsLeft = Math.round(diff / 60000);
            fireNotification(race.name, session.name, minsLeft);
          }
        }
      }
    }

    checkSessions();
    const id = setInterval(checkSessions, 60_000);
    return () => clearInterval(id);
  }, [enabled, permission, minutesBefore]);

  return {
    enabled,
    setEnabled,
    minutesBefore,
    setMinutesBefore,
    permission,
    requestPermission,
    supported,
  };
}
