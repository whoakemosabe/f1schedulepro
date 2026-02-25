import type { Race } from "../types";
import {
  formatSessionDate,
  formatSessionTime,
  getTimezoneAbbr,
} from "../utils/time";
import { useCountdown } from "../hooks/useCountdown";
import { getNextSession } from "../utils/time";
import { getFlagUrl } from "../utils/countries";
import { MapPin, Clock, Zap, Flag } from "lucide-react";

interface Props {
  race: Race;
  timezone: string;
}

export function RaceDetail({ race, timezone }: Props) {
  const nextSession = getNextSession(race);
  const countdown = useCountdown(nextSession?.dateUtc ?? null);
  const tzAbbr = getTimezoneAbbr(timezone);

  return (
    <div className="px-3 py-3 space-y-2.5 max-w-2xl mx-auto">
      {/* Hero */}
      <div className="glass rounded-xl p-3 space-y-2.5">
        <div className="flex items-center gap-2.5">
          <img
            src={getFlagUrl(race.circuit.countryCode)}
            alt={race.circuit.country}
            className="w-10 h-7 rounded object-cover shadow-md"
          />
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="text-[9px] font-display font-semibold text-text-muted tracking-widest">
                {race.type === "testing" ? "PRE-SEASON" : `ROUND ${race.round}`}
              </span>
              {race.type === "sprint" && (
                <span className="inline-flex items-center gap-px text-[8px] font-bold text-warning bg-warning/8 px-1 py-px rounded border border-warning/15">
                  <Zap size={7} /> SPRINT
                </span>
              )}
            </div>
            <h2 className="font-display text-base font-bold tracking-wide text-text leading-tight truncate">
              {race.name}
            </h2>
          </div>
        </div>

        <div className="h-px bg-linear-to-r from-transparent via-border-light to-transparent" />

        <p className="text-[8px] text-text-muted uppercase tracking-[0.15em] leading-relaxed">
          {race.officialName}
        </p>

        <div className="flex items-center gap-1.5 text-[11px] text-text-secondary">
          <MapPin size={11} className="text-highlight shrink-0" />
          <span className="truncate">
            {race.circuit.name} — {race.circuit.location}, {race.circuit.country}
          </span>
        </div>

        {/* Countdown */}
        {countdown && nextSession && countdown.total > 0 && (
          <div className="glass rounded-lg p-3 text-center space-y-1.5 border border-accent/10">
            <p className="text-[9px] text-text-muted uppercase tracking-[0.2em] font-medium">
              {nextSession.name} starts in
            </p>
            <div className="flex justify-center gap-2.5">
              {[
                { val: countdown.days, label: "D" },
                { val: countdown.hours, label: "H" },
                { val: countdown.minutes, label: "M" },
                { val: countdown.seconds, label: "S" },
              ].map((unit) => (
                <div key={unit.label} className="text-center">
                  <div className="font-display text-xl font-bold text-accent tabular-nums leading-none num-tick">
                    {String(unit.val).padStart(2, "0")}
                  </div>
                  <div className="text-[8px] text-text-muted tracking-widest mt-0.5 font-medium">
                    {unit.label}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Session schedule */}
      <div className="glass rounded-xl overflow-hidden">
        <div className="px-3 py-2 flex items-center justify-between border-b border-glass-border">
          <h3 className="font-display text-[10px] font-semibold tracking-widest text-text-muted uppercase flex items-center gap-1.5">
            <Clock size={10} />
            Sessions
          </h3>
          <span className="text-[9px] text-highlight font-mono tracking-wider">{tzAbbr}</span>
        </div>

        <div className="divide-y divide-glass-border">
          {race.sessions.map((session, i) => {
            const isPast = new Date(session.dateUtc) < new Date();
            const isNext = nextSession && session.dateUtc === nextSession.dateUtc;
            const isRace = session.name === "Race";
            const isSprint = session.name === "Sprint" || session.name === "Sprint Qualifying";

            return (
              <div
                key={i}
                className={`
                  flex items-center justify-between px-3 py-2 transition-colors duration-100
                  ${isNext ? "bg-accent/8 border-l-2 border-l-accent" : "border-l-2 border-l-transparent"}
                  ${isPast ? "opacity-35" : ""}
                `}
              >
                <div className="flex items-center gap-2">
                  {isRace ? (
                    <Flag size={11} className="text-accent" />
                  ) : isSprint ? (
                    <Zap size={11} className="text-warning" />
                  ) : (
                    <div className="w-[11px]" />
                  )}
                  <span
                    className={`text-[12px] font-medium ${
                      isRace ? "text-text font-semibold" : isSprint ? "text-warning" : "text-text-secondary"
                    }`}
                  >
                    {session.name}
                  </span>
                  {isNext && (
                    <span className="text-[7px] font-bold text-accent bg-accent/12 px-1 py-px rounded tracking-widest uppercase border border-accent/15">
                      Next
                    </span>
                  )}
                </div>
                <div className="text-right tabular-nums">
                  <div className="text-[11px] text-text-secondary">{formatSessionTime(session.dateUtc, timezone)}</div>
                  <div className="text-[9px] text-text-muted">{formatSessionDate(session.dateUtc, timezone)}</div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
