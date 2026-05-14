import { useState, useEffect } from "react";
import type { Race } from "../types";
import {
  formatSessionDate,
  formatSessionTime,
  getTimezoneAbbr,
  SESSION_DURATION_MIN,
} from "../utils/time";
import { useCountdown } from "../hooks/useCountdown";
import { getNextSession, getRaceStatus } from "../utils/time";
import { getFlagUrl } from "../utils/countries";
import { getCircuitStats } from "../data/circuits";
import {
  MapPin,
  Clock,
  Zap,
  Flag,
  CheckCircle2,
  Check,
  Moon,
  Ruler,
  CornerDownRight,
  Trophy,
  ExternalLink,
  CircleDot,
  Crosshair,
} from "lucide-react";
import { FlipDigit } from "./FlipDigit";

interface Props {
  race: Race;
  timezone: string;
  globalNextSessionDate: string | null;
}

function getSessionColors(name: string) {
  if (name === "Race") return { border: "border-live/25", text: "text-live", bg: "bg-live/8" };
  if (name.includes("Sprint")) return { border: "border-warning/25", text: "text-warning", bg: "bg-warning/8" };
  if (name === "Qualifying") return { border: "border-highlight/25", text: "text-highlight", bg: "bg-highlight/8" };
  return { border: "border-highlight/20", text: "text-highlight", bg: "bg-highlight/5" };
}

const TRACK_TYPE_LABEL: Record<string, string> = {
  permanent: "Permanent",
  street: "Street Circuit",
  hybrid: "Hybrid",
};

const SESSION_URL_SUFFIX: Record<string, string> = {
  "Practice 1": "practice/1",
  "Practice 2": "practice/2",
  "Practice 3": "practice/3",
  "Sprint Qualifying": "sprint-qualifying",
  "Sprint": "sprint-results",
  "Qualifying": "qualifying",
  "Race": "race-result",
};

function getResultsUrl(race: Race, sessionName: string): string | null {
  if (!race.f1ResultsId || !race.f1ResultsSlug) return null;
  const suffix = SESSION_URL_SUFFIX[sessionName];
  if (!suffix) return null;
  return `https://www.formula1.com/en/results/2026/races/${race.f1ResultsId}/${race.f1ResultsSlug}/${suffix}`;
}

export function RaceDetail({ race, timezone, globalNextSessionDate }: Props) {
  const nextSession = getNextSession(race);
  const countdown = useCountdown(nextSession?.dateUtc ?? null);
  const raceStatus = getRaceStatus(race);
  const isCompleted = raceStatus === "completed";
  const tzAbbr = getTimezoneAbbr(timezone);
  const stats = getCircuitStats(race.circuit.id);

  // Reactive tick so live detection updates without needing a re-render from a parent
  const [nowMs, setNowMs] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNowMs(Date.now()), 10_000);
    return () => clearInterval(id);
  }, []);

  const liveSession = race.sessions.find((s) => {
    const startMs = new Date(s.dateUtc).getTime();
    const durationMs = (SESSION_DURATION_MIN[s.name] ?? 90) * 60 * 1000;
    return nowMs >= startMs && nowMs <= startMs + durationMs;
  });

  return (
    <div className="px-3 py-4 space-y-2.5 max-w-2xl mx-auto">
      {/* Hero card */}
      <div className={`glass-light rounded-xl p-4 space-y-3 border-l-[3px] ${isCompleted ? "border-l-success/50" : raceStatus === "active" ? "border-l-live" : "border-l-highlight"}`}>
        <div className="flex items-center gap-3">
          <img
            src={getFlagUrl(race.circuit.countryCode)}
            alt={race.circuit.country}
            className="w-12 h-8 rounded-lg object-cover shadow-md"
          />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-display font-semibold text-text-muted tracking-widest">
                {race.type === "testing" ? "PRE-SEASON" : `ROUND ${race.round}`}
              </span>
              {stats?.night && (
                <span className="inline-flex items-center gap-1 text-[11px] font-bold text-highlight/80 bg-highlight/8 px-1.5 py-0.5 rounded border border-highlight/15">
                  <Moon size={11} /> NIGHT
                </span>
              )}
              {race.type === "sprint" && (
                <span className="inline-flex items-center gap-0.5 text-[11px] font-bold text-warning bg-warning/10 px-1.5 py-0.5 rounded border border-warning/15">
                  <Zap size={11} /> SPRINT
                </span>
              )}
            </div>
            <h2 className="font-display text-lg font-bold tracking-wide text-text leading-tight truncate">
              {race.name}
            </h2>
          </div>
        </div>

        <div className="h-px bg-linear-to-r from-transparent via-border-light to-transparent" />

        <p className="text-[11px] text-text-muted uppercase tracking-[0.15em] leading-relaxed">
          {race.officialName}
        </p>

        <div className="flex items-center gap-2 text-[13px] text-text-secondary">
          <MapPin size={13} className="text-highlight shrink-0" />
          <span className="truncate">
            {race.circuit.name} — {race.circuit.location}, {race.circuit.country}
          </span>
        </div>

        {/* Race completed banner */}
        {isCompleted && (
          <div className="glass-light rounded-lg px-4 min-h-26 flex flex-col items-center justify-center text-center space-y-2 border border-success/20">
            <CheckCircle2 size={32} className="text-success" />
            <p className="font-display text-sm font-bold text-success tracking-widest uppercase">
              Race Complete
            </p>
          </div>
        )}

        {/* Live session */}
        {!isCompleted && liveSession && (() => {
          const colors = getSessionColors(liveSession.name);
          return (
            <div className={`glass-light rounded-lg px-4 min-h-26 flex flex-col items-center justify-center text-center space-y-2 border border-live/25 bg-live/8`}>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-live live-dot" />
                <span className="text-[11px] font-bold uppercase tracking-widest text-live">LIVE</span>
              </div>
              <p className={`font-display text-sm font-bold tracking-wide ${colors.text}`}>
                {liveSession.name}
              </p>
            </div>
          );
        })()}

        {/* Countdown */}
        {!isCompleted && !liveSession && countdown && nextSession && countdown.total > 0 && (() => {
          const isNext = globalNextSessionDate && nextSession.dateUtc === globalNextSessionDate;
          const colors = isNext
            ? getSessionColors(nextSession.name)
            : { border: "border-border-light", text: "text-text-muted", bg: "" };
          return (
            <div className={`glass-light rounded-lg px-4 min-h-26 flex flex-col items-center justify-center text-center space-y-2 border ${colors.border} ${colors.bg}`}>
              <p className={`text-[11px] uppercase tracking-[0.2em] font-medium ${colors.text}`}>
                {nextSession.name} starts in
              </p>
              <div className="flex justify-center gap-4">
                {[
                  { val: countdown.days, label: "D" },
                  { val: countdown.hours, label: "H" },
                  { val: countdown.minutes, label: "M" },
                  { val: countdown.seconds, label: "S" },
                ].map((unit) => (
                  <div key={unit.label} className="text-center">
                    <div className={`font-display text-2xl font-bold tabular-nums leading-none ${colors.text}`}>
                      <FlipDigit value={String(unit.val).padStart(2, "0")} />
                    </div>
                    <div className="text-[11px] text-text-muted tracking-widest mt-1 font-medium">
                      {unit.label}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        })()}

        {/* Track stats — slim inline strip */}
        {stats && (
          <>
            <div className="h-px bg-linear-to-r from-transparent via-border-light to-transparent" />

            <div className="flex items-center justify-between text-[11px]">
              <div className="flex items-center gap-3">
                <span className="flex items-center gap-1 text-text-secondary">
                  <Ruler size={11} className="text-highlight" />
                  <span className="font-semibold text-text tabular-nums">{stats.lengthKm}</span> km
                </span>
                <span className="text-border-light">|</span>
                <span className="flex items-center gap-1 text-text-secondary">
                  <CornerDownRight size={11} className="text-highlight" />
                  <span className="font-semibold text-text tabular-nums">{stats.turns}</span> turns
                </span>
                <span className="text-border-light">|</span>
                <span className="flex items-center gap-1 text-text-secondary">
                  <Zap size={11} className="text-warning" />
                  <span className="font-semibold text-text tabular-nums">{stats.drsZones}</span> DRS
                </span>
              </div>
              <span className="text-text-muted font-medium">{TRACK_TYPE_LABEL[stats.type]}</span>
            </div>

            {stats.lapRecord && (
              <div className="flex items-center justify-between text-[11px]">
                <div className="flex items-center gap-1.5 text-text-secondary">
                  <Trophy size={11} className="text-warning" />
                  Lap record
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-display font-bold text-accent tabular-nums">{stats.lapRecord.time}</span>
                  <span className="text-text-muted/40">|</span>
                  <a
                    href={stats.lapRecord.driverUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-text-secondary active:text-accent transition-colors duration-100"
                  >
                    {stats.lapRecord.driver} <span className="text-text-muted">'{String(stats.lapRecord.year).slice(2)}</span>
                    <ExternalLink size={9} className="text-text-muted/40 shrink-0" />
                  </a>
                </div>
              </div>
            )}

            <a
              href={stats.wiki}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-1.5 text-[11px] font-semibold text-highlight tracking-wide bg-surface-light border border-glass-border px-3 py-1.5 rounded-md active:scale-95 transition-all duration-100"
            >
              View on Wikipedia <ExternalLink size={11} />
            </a>
          </>
        )}
      </div>

      {/* Session schedule */}
      <div className="glass-light rounded-xl overflow-hidden border-l-[3px] border-l-highlight/40">
        <div className="px-4 py-2.5 flex items-center justify-between border-b border-glass-border">
          <h3 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase flex items-center gap-2">
            <Clock size={12} />
            Sessions
          </h3>
          <span className="text-[11px] text-highlight font-mono tracking-wider">{tzAbbr}</span>
        </div>

        <div className="divide-y divide-glass-border">
          {race.sessions.map((session) => {
            // A session is "live" if we're inside its start→start+duration window.
            // Re-use the already-computed liveSession rather than recalculating.
            const isLive = liveSession?.name === session.name;
            // Mark past only after the live window has closed, using the same
            // nowMs reference as liveSession so the two states never overlap.
            const isPast = !isLive && new Date(session.dateUtc).getTime() < nowMs;
            const isGlobalNext = globalNextSessionDate && session.dateUtc === globalNextSessionDate;
            const isRace = session.name === "Race";
            const isSprint = session.name === "Sprint" || session.name === "Sprint Qualifying";
            const isQualifying = session.name === "Qualifying";
            // Only offer a results link once the session is fully in the past
            // (not while it's live — results don't exist yet).
            const resultsUrl = isPast ? getResultsUrl(race, session.name) : null;

            const content = (
              <>
                <div className="flex items-center gap-2.5">
                  {isLive ? (
                    <span className="w-2.5 h-2.5 rounded-full bg-live live-dot shrink-0" />
                  ) : isPast ? (
                    <Check size={14} className="text-success shrink-0" />
                  ) : isRace ? (
                    <Flag size={14} className="text-accent" />
                  ) : isSprint ? (
                    <Zap size={14} className="text-warning" />
                  ) : isQualifying ? (
                    <Crosshair size={14} className="text-highlight" />
                  ) : (
                    <CircleDot size={14} className="text-highlight/50" />
                  )}
                  <span
                    className={`text-sm font-medium ${
                      isLive ? "text-live font-semibold" : isPast ? "text-text-muted line-through" : isRace ? "text-text font-semibold" : isSprint ? "text-warning" : isQualifying ? "text-highlight" : "text-text-secondary"
                    }`}
                  >
                    {session.name}
                  </span>
                  {isGlobalNext && !isLive && (
                    <span className="text-[11px] font-bold text-highlight bg-highlight/12 px-1.5 py-0.5 rounded tracking-widest uppercase border border-highlight/15">
                      Next
                    </span>
                  )}
                  {resultsUrl && (
                    <ExternalLink size={10} className="text-text-muted/50 shrink-0" />
                  )}
                </div>
                <div className="flex items-center gap-2 tabular-nums">
                  <div className="text-right">
                    <div className={`text-[13px] ${isPast ? "text-text-muted line-through" : isLive ? "text-live" : "text-text-secondary"}`}>{formatSessionTime(session.dateUtc, timezone)}</div>
                    <div className="text-[11px] text-text-muted">{formatSessionDate(session.dateUtc, timezone)}</div>
                  </div>
                  {!isPast && !isLive && (
                    <Clock size={13} className="text-text-muted/30 shrink-0" />
                  )}
                </div>
              </>
            );

            const className = `
              flex items-center justify-between px-4 py-2.5 transition-colors duration-100
              ${isLive ? "bg-live/8 border-l-[3px] border-l-live" : isGlobalNext ? "bg-highlight/8 border-l-[3px] border-l-highlight" : "border-l-[3px] border-l-transparent"}
              ${isPast ? "opacity-40" : ""}
              ${resultsUrl ? "active:bg-surface-light" : ""}
            `;

            return resultsUrl ? (
              <a
                key={session.name}
                href={resultsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={className}
              >
                {content}
              </a>
            ) : (
              <div key={session.name} className={className}>
                {content}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
