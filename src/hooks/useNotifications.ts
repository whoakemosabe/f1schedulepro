import { useState, useEffect, useCallback, useRef } from "react";
import schedule2026 from "../data/schedule2026";

const STORAGE_ENABLED = "f1sp-notifications-enabled";
const STORAGE_BEFORE = "f1sp-notify-before";
const STORAGE_NOTIFIED = "f1sp-notified-sessions";

const PUSH_SERVER = (import.meta.env.VITE_PUSH_SERVER_URL as string | undefined)
  ?.replace(/\/$/, "") || "";

export interface NotificationSettings {
  enabled: boolean;
  setEnabled: (v: boolean) => void;
  minutesBefore: number;
  setMinutesBefore: (m: number) => void;
  permission: NotificationPermission;
  requestPermission: () => Promise<void>;
  supported: boolean;
  hasPushServer: boolean;
}

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from([...atob(base64)].map((c) => c.charCodeAt(0)));
}

async function subscribeToPush(minutesBefore: number): Promise<void> {
  if (!PUSH_SERVER || !("serviceWorker" in navigator) || !("PushManager" in window)) return;
  try {
    const res = await fetch(`${PUSH_SERVER}/vapid-public-key`);
    if (!res.ok) return;
    const { publicKey } = (await res.json()) as { publicKey: string };
    const reg = await navigator.serviceWorker.ready;
    const existing = await reg.pushManager.getSubscription();
    const sub = existing ?? await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    });
    await fetch(`${PUSH_SERVER}/subscribe`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ subscription: sub.toJSON(), minutesBefore }),
    });
  } catch {
    // push subscription failed — interval fallback will handle it
  }
}

async function unsubscribeFromPush(): Promise<void> {
  if (!PUSH_SERVER || !("serviceWorker" in navigator)) return;
  try {
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.getSubscription();
    if (!sub) return;
    await fetch(`${PUSH_SERVER}/unsubscribe`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ endpoint: sub.endpoint }),
    });
    await sub.unsubscribe();
  } catch {
    // ignore
  }
}

async function updatePushSettings(minutesBefore: number): Promise<void> {
  if (!PUSH_SERVER || !("serviceWorker" in navigator)) return;
  try {
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.getSubscription();
    if (!sub) return;
    await fetch(`${PUSH_SERVER}/update-settings`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ endpoint: sub.endpoint, minutesBefore }),
    });
  } catch {
    // ignore
  }
}

export function useNotifications(): NotificationSettings {
  const supported = typeof window !== "undefined" && "Notification" in window;

  const [permission, setPermission] = useState<NotificationPermission>(
    supported ? Notification.permission : "denied"
  );
  const [enabled, setEnabledState] = useState(() => {
    try { return localStorage.getItem(STORAGE_ENABLED) === "true"; } catch { return false; }
  });
  const [minutesBefore, setMinutesBeforeState] = useState(() => {
    try {
      const parsed = parseInt(localStorage.getItem(STORAGE_BEFORE) ?? "", 10);
      return Number.isFinite(parsed) ? parsed : 30;
    } catch { return 30; }
  });

  const enabledRef = useRef(enabled);
  enabledRef.current = enabled;
  const permissionRef = useRef(permission);
  permissionRef.current = permission;
  const minutesBeforeRef = useRef(minutesBefore);
  minutesBeforeRef.current = minutesBefore;

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

  // Push subscription: subscribe when enabled+granted, unsubscribe when disabled
  useEffect(() => {
    if (!PUSH_SERVER || permissionRef.current !== "granted") return;
    if (enabled) {
      void subscribeToPush(minutesBeforeRef.current);
    } else {
      void unsubscribeFromPush();
    }
  }, [enabled]); // eslint-disable-line react-hooks/exhaustive-deps

  // Push settings update when minutesBefore changes
  useEffect(() => {
    if (!PUSH_SERVER || !enabledRef.current || permissionRef.current !== "granted") return;
    void updatePushSettings(minutesBefore);
  }, [minutesBefore]); // eslint-disable-line react-hooks/exhaustive-deps

  // Also subscribe when permission first becomes granted (on return from requestPermission)
  useEffect(() => {
    if (!PUSH_SERVER || permission !== "granted" || !enabledRef.current) return;
    void subscribeToPush(minutesBeforeRef.current);
  }, [permission]);

  // Interval fallback: fires when app is open (always active, push server is additive)
  useEffect(() => {
    if (!enabled || permission !== "granted") return;

    function getNotifiedSet(): Set<string> {
      try {
        return new Set(JSON.parse(localStorage.getItem(STORAGE_NOTIFIED) ?? "[]") as string[]);
      } catch { return new Set(); }
    }

    function markNotified(key: string) {
      const set = getNotifiedSet();
      set.add(key);
      localStorage.setItem(STORAGE_NOTIFIED, JSON.stringify([...set].slice(-100)));
    }

    function fireNotification(raceName: string, sessionName: string, minsLeft: number) {
      const opts: NotificationOptions = {
        body: `${raceName} \u2014 ${sessionName} starts in ${minsLeft} minute${minsLeft !== 1 ? "s" : ""}`,
        icon: "/pwa-192x192.png",
        badge: "/pwa-192x192.png",
        tag: `session-${raceName}-${sessionName}`,
        vibrate: [200, 100, 200],
      } as NotificationOptions;

      if ("serviceWorker" in navigator && navigator.serviceWorker.controller) {
        void navigator.serviceWorker.ready.then((reg) => reg.showNotification("F1 Schedule Pro", opts));
      } else {
        new Notification("F1 Schedule Pro", opts);
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
            fireNotification(race.name, session.name, Math.round(diff / 60000));
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
    hasPushServer: Boolean(PUSH_SERVER),
  };
}
