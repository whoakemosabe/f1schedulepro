import { useState, useEffect, useMemo } from "react";
import type { TabId } from "../types";
import schedule2026 from "../data/schedule2026";
import { getNextSession, getTimezoneAbbr } from "../utils/time";
import { getFlagUrl } from "../utils/countries";
import { useCountdown } from "../hooks/useCountdown";
import { FlipDigit } from "./FlipDigit";

const TITLES: Record<TabId, string> = {
  schedule: "Schedule",
  standings: "Standings",
  news: "News",
  settings: "Settings",
};

// Estimated session durations in minutes
const SESSION_DURATION_MIN: Record<string, number> = {
  "Practice 1": 60,
  "Practice 2": 60,
  "Practice 3": 60,
  "Sprint Qualifying": 60,
  "Sprint": 60,
  "Qualifying": 60,
  "Race": 150,
};

// Session-type badge (secondary info only)
function getSessionBadge(name: string) {
  if (name === "Race") return "bg-accent/15 text-accent border-accent/25";
  if (name.includes("Sprint")) return "bg-warning/15 text-warning border-warning/25";
  if (name === "Qualifying") return "bg-highlight/15 text-highlight border-highlight/25";
  return "bg-highlight/10 text-highlight/70 border-highlight/15";
}

// Short session labels for the compact header strip
function getShortSession(name: string) {
  const map: Record<string, string> = {
    "Practice 1": "FP1", "Practice 2": "FP2", "Practice 3": "FP3",
    "Sprint Qualifying": "SQ", "Qualifying": "QUALI",
  };
  return map[name] ?? name;
}

interface HeaderProps {
  activeTab: TabId;
  timezone: string;
}

export function Header({ activeTab, timezone }: HeaderProps) {
  // Live clock — ticks every second
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const timeStr = now.toLocaleTimeString("en-US", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });
  const tzAbbr = getTimezoneAbbr(timezone);

  // Find live session or next upcoming session
  const sessionInfo = useMemo(() => {
    const nowMs = now.getTime();
    for (const race of schedule2026) {
      for (const session of race.sessions) {
        const startMs = new Date(session.dateUtc).getTime();
        const durationMs = (SESSION_DURATION_MIN[session.name] ?? 90) * 60 * 1000;
        if (nowMs >= startMs && nowMs <= startMs + durationMs) {
          return { live: true, session, raceName: race.name, countryCode: race.circuit.countryCode };
        }
      }
      const ns = getNextSession(race);
      if (ns) {
        return { live: false, session: ns, raceName: race.name, countryCode: race.circuit.countryCode };
      }
    }
    return null;
  }, [now]);

  const countdown = useCountdown(
    sessionInfo && !sessionInfo.live ? sessionInfo.session.dateUtc : null
  );
  const hasCountdown = countdown && countdown.total > 0;

  return (
    <header className="glass safe-top fixed top-0 left-0 right-0 z-50 border-x-0! border-t-0! accent-bar">
      {/* Main bar */}
      <div className="flex items-center gap-3 px-4 py-2">
        <div className="w-8 h-8 rounded-lg bg-accent flex items-center justify-center shadow-[0_0_16px_rgba(225,6,0,0.35)]">
          <span className="text-white font-display font-black text-[11px] leading-none">F1</span>
        </div>
        <div className="flex items-baseline gap-2.5">
          <h1 className="font-display text-[15px] font-bold tracking-widest text-text uppercase leading-none">
            {TITLES[activeTab]}
          </h1>
          <span className="text-[11px] text-text-muted tracking-[0.2em] uppercase font-medium">
            2026
          </span>
        </div>
        <div className="ml-auto flex items-center gap-1.5">
          <span className="font-display text-[14px] font-bold tabular-nums text-text leading-none">
            {timeStr.replace(/\s?(AM|PM)/, "")}
            <span className="text-[10px] text-text-muted font-medium ml-0.5">{timeStr.includes("PM") ? "PM" : "AM"}</span>
          </span>
          <span className="text-[10px] text-text-muted tracking-wider font-medium">{tzAbbr}</span>
        </div>
      </div>

      {/* Session strip — LIVE (red) */}
      {sessionInfo?.live && (
        <>
          <div className="h-px bg-linear-to-r from-transparent via-live/40 to-transparent" />
          <div className="flex items-center gap-2 px-4 py-1.5 bg-live/10">
            <span className="flex items-center gap-1.5 shrink-0">
              <span className="w-2 h-2 rounded-full bg-live live-dot" />
              <span className="text-[11px] font-bold uppercase tracking-widest text-live">LIVE</span>
            </span>
            <span className="text-border-light shrink-0">|</span>
            <img
              src={getFlagUrl(sessionInfo.countryCode)}
              alt=""
              className="w-5 h-3.5 rounded-sm object-cover shrink-0 shadow-sm"
            />
            <span className="text-[12px] font-semibold text-text truncate min-w-0">
              {sessionInfo.raceName}
            </span>
            <span className={`text-[10px] font-bold uppercase tracking-widest px-1.5 py-0.5 rounded border ml-auto shrink-0 ${getSessionBadge(sessionInfo.session.name)}`}>
              {getShortSession(sessionInfo.session.name)}
            </span>
          </div>
        </>
      )}

      {/* Session strip — UPCOMING countdown (cyan) */}
      {sessionInfo && !sessionInfo.live && hasCountdown && (
        <>
          <div className="h-px bg-linear-to-r from-transparent via-highlight/30 to-transparent" />
          <div className="flex items-center gap-2 px-4 py-1.5 bg-highlight/5">
            <img
              src={getFlagUrl(sessionInfo.countryCode)}
              alt=""
              className="w-5 h-3.5 rounded-sm object-cover shrink-0 shadow-sm"
            />
            <span className="text-[12px] font-semibold text-text truncate min-w-0">
              {sessionInfo.raceName}
            </span>
            <span className={`text-[10px] font-bold uppercase tracking-widest px-1.5 py-0.5 rounded border shrink-0 ${getSessionBadge(sessionInfo.session.name)}`}>
              {getShortSession(sessionInfo.session.name)}
            </span>
            <div className="flex items-center gap-0.5 ml-auto shrink-0">
              {[
                ...(countdown!.days > 0 ? [{ val: countdown!.days, label: "d", pad: false }] : []),
                { val: countdown!.hours, label: "h", pad: true },
                { val: countdown!.minutes, label: "m", pad: true },
                { val: countdown!.seconds, label: "s", pad: true },
              ].map((unit) => (
                <div key={unit.label} className="flex items-center">
                  <span className="font-display text-[12px] font-bold text-highlight bg-highlight/10 rounded px-0.5 py-0.5 tabular-nums leading-none border border-highlight/15">
                    <FlipDigit value={unit.pad ? String(unit.val).padStart(2, "0") : String(unit.val)} />
                  </span>
                  <span className="text-[9px] text-text-muted font-semibold uppercase ml-0.5 mr-0.5">{unit.label}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </header>
  );
}
