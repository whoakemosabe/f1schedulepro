import type { Race, RaceStatus, Session } from "../types";

export const SESSION_DURATION_MIN: Record<string, number> = {
  "Practice 1": 60,
  "Practice 2": 60,
  "Practice 3": 60,
  "Sprint Qualifying": 60,
  "Sprint": 60,
  "Qualifying": 60,
  "Race": 150,
};

export const TIMEZONE_OPTIONS = [
  { value: "UTC", label: "UTC" },
  { value: "Atlantic/Reykjavik", label: "GMT" },
  { value: "America/New_York", label: "Eastern (ET)" },
  { value: "America/Chicago", label: "Central (CT)" },
  { value: "America/Denver", label: "Mountain (MT)" },
  { value: "America/Los_Angeles", label: "Pacific (PT)" },
  { value: "Europe/London", label: "London (GMT/BST)" },
  { value: "Europe/Paris", label: "Central Europe (CET)" },
  { value: "Europe/Helsinki", label: "Eastern Europe (EET)" },
  { value: "Asia/Dubai", label: "Dubai (GST)" },
  { value: "Asia/Kolkata", label: "India (IST)" },
  { value: "Asia/Singapore", label: "Singapore (SGT)" },
  { value: "Asia/Tokyo", label: "Japan (JST)" },
  { value: "Australia/Sydney", label: "Sydney (AEST)" },
] as const;

export function formatSessionDate(
  utcDateStr: string,
  timezone: string
): string {
  const date = new Date(utcDateStr);
  return date.toLocaleDateString("en-US", {
    timeZone: timezone,
    weekday: "short",
    month: "short",
    day: "numeric",
  });
}

export function formatSessionTime(
  utcDateStr: string,
  timezone: string
): string {
  const date = new Date(utcDateStr);
  return date.toLocaleTimeString("en-US", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
  });
}

export function getRaceStatus(race: Race): RaceStatus {
  const now = new Date();
  const sessions = race.sessions;
  if (sessions.length === 0) return "upcoming";

  const firstSession = new Date(sessions[0].dateUtc);
  const lastSession = new Date(sessions[sessions.length - 1].dateUtc);
  // Add ~3 hours after race start to account for race duration
  const raceEnd = new Date(lastSession.getTime() + 3 * 60 * 60 * 1000);

  if (now > raceEnd) return "completed";
  if (now >= firstSession && now <= raceEnd) return "active";
  return "upcoming";
}

export function getNextSession(race: Race): Session | null {
  const now = new Date();
  for (const session of race.sessions) {
    if (new Date(session.dateUtc) > now) return session;
  }
  return null;
}

export interface Countdown {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  total: number;
}

export function getCountdown(targetUtc: string): Countdown {
  const diff = new Date(targetUtc).getTime() - Date.now();
  if (diff <= 0) return { days: 0, hours: 0, minutes: 0, seconds: 0, total: 0 };

  return {
    days: Math.floor(diff / (1000 * 60 * 60 * 24)),
    hours: Math.floor((diff / (1000 * 60 * 60)) % 24),
    minutes: Math.floor((diff / (1000 * 60)) % 60),
    seconds: Math.floor((diff / 1000) % 60),
    total: diff,
  };
}

export function getTimezoneAbbr(timezone: string): string {
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: timezone,
      timeZoneName: "short",
    }).formatToParts(new Date());
    return parts.find((p) => p.type === "timeZoneName")?.value ?? timezone;
  } catch {
    return timezone;
  }
}
