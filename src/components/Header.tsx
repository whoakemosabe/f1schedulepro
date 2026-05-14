import { useState, useEffect, useMemo } from "react";
import type { TabId } from "../types";
import schedule2026 from "../data/schedule2026";
import { getNextSession, getTimezoneAbbr, SESSION_DURATION_MIN } from "../utils/time";
import { getFlagUrl } from "../utils/countries";
import { useCountdown } from "../hooks/useCountdown";
import { FlipDigit } from "./FlipDigit";

const TITLES: Record<TabId, string> = {
  schedule: "Schedule",
  standings: "Standings",
  news: "News",
  settings: "Settings",
};

// Session-type badge colors
function getSessionBadge(name: string) {
  if (name === "Race") return { bg: "bg-accent/12", text: "text-accent", border: "border-accent/20" };
  if (name.includes("Sprint")) return { bg: "bg-warning/12", text: "text-warning", border: "border-warning/20" };
  if (name === "Qualifying") return { bg: "bg-highlight/12", text: "text-highlight", border: "border-highlight/20" };
  return { bg: "bg-highlight/8", text: "text-highlight/70", border: "border-highlight/12" };
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
  // Live clock — ticks every second for the time display
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

  // Slow tick (every 10 s) for session detection — "which session is next"
  // almost never changes, so re-scanning 125 sessions every second is wasteful.
  const [sessionTick, setSessionTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setSessionTick((t) => t + 1), 10_000);
    return () => clearInterval(id);
  }, []);

  // Find live session or next upcoming session
  const sessionInfo = useMemo(() => {
    const nowMs = Date.now();
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
  }, [sessionTick]);

  const countdown = useCountdown(
    sessionInfo && !sessionInfo.live ? sessionInfo.session.dateUtc : null
  );
  const hasCountdown = countdown && countdown.total > 0;

  return (
    <header className="glass safe-top fixed top-0 left-0 right-0 z-50 border-x-0! border-t-0! accent-bar">
      {/* Main bar */}
      <div className="flex items-center gap-3 px-4 py-2">
        <div className="shrink-0 w-8 h-8 rounded-lg bg-accent flex items-center justify-center shadow-[0_0_16px_rgba(225,6,0,0.35)]">
          <span className="text-white font-display font-black text-[11px] leading-none">F1</span>
        </div>
        <div className="shrink-0 flex items-center gap-2.5">
          <h1 className="font-display text-[15px] font-bold tracking-widest text-text uppercase leading-none">
            {TITLES[activeTab]}
          </h1>
          <span className="text-[11px] text-text-muted tracking-[0.2em] uppercase font-medium leading-none">
            2026
          </span>
        </div>
        <div className="ml-auto shrink-0 flex items-center gap-2 overflow-hidden">
          <span className="font-mono text-[13px] font-bold text-text leading-none w-[7.5ch] shrink-0">
            {timeStr.replace(/\s?(AM|PM)/, "").trim()}
          </span>
          <span className="text-[10px] text-text-muted font-mono font-medium leading-none shrink-0 w-[2ch]">
            {timeStr.includes("PM") ? "PM" : "AM"}
          </span>
          <span className="text-[9px] font-bold tracking-wider text-highlight/80 bg-highlight/10 border border-highlight/20 px-1.5 py-0.5 rounded-md leading-none whitespace-nowrap shrink-0">
            {tzAbbr}
          </span>
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
            {(() => {
              const badge = getSessionBadge(sessionInfo.session.name);
              return (
                <span className={`text-[10px] font-bold uppercase tracking-widest px-1.5 py-0.5 rounded border ml-auto shrink-0 ${badge.bg} ${badge.text} ${badge.border}`}>
                  {getShortSession(sessionInfo.session.name)}
                </span>
              );
            })()}
          </div>
        </>
      )}

      {/* Session strip — UPCOMING countdown (cyan) */}
      {sessionInfo && !sessionInfo.live && hasCountdown && (
        <>
          <div className="h-px bg-linear-to-r from-transparent via-highlight/30 to-transparent" />
          <div className="flex items-center justify-center gap-2 px-4 py-1.5 bg-highlight/5">
            <img
              src={getFlagUrl(sessionInfo.countryCode)}
              alt=""
              className="w-5 h-3.5 rounded-sm object-cover shrink-0 shadow-sm"
            />
            <span className="text-[12px] font-semibold text-text shrink-0">
              {sessionInfo.raceName}
            </span>
            <span className="text-text-muted/40 shrink-0">·</span>
            {(() => {
              const badge = getSessionBadge(sessionInfo.session.name);
              return (
                <span className={`text-[10px] font-bold uppercase tracking-widest px-1.5 py-0.5 rounded shrink-0 ${badge.bg} ${badge.text} border ${badge.border}`}>
                  {getShortSession(sessionInfo.session.name)}
                </span>
              );
            })()}
            <span className="text-text-muted/40 shrink-0">·</span>
            <div className="flex items-center gap-0.5 shrink-0">
              {[
                ...(countdown!.days > 0 ? [{ val: countdown!.days, label: "d", pad: false }] : []),
                { val: countdown!.hours, label: "h", pad: true },
                { val: countdown!.minutes, label: "m", pad: true },
                { val: countdown!.seconds, label: "s", pad: true },
              ].map((unit) => (
                <div key={unit.label} className="flex items-center">
                  <span className="font-display text-[12px] font-bold text-highlight tabular-nums leading-none">
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
