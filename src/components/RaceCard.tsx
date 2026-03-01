import { useRef, useEffect } from "react";
import type { Race, RaceStatus, Session } from "../types";
import { formatSessionDate, formatSessionTime, getTimezoneAbbr } from "../utils/time";
import { useCountdown } from "../hooks/useCountdown";
import { getFlagUrl } from "../utils/countries";
import { getCircuitStats } from "../data/circuits";
import { MapPin, Clock, Zap, Moon, ChevronRight, Check } from "lucide-react";
import { FlipDigit } from "./FlipDigit";

interface Props {
  race: Race;
  status: RaceStatus;
  nextSession: Session | null;
  timezone: string;
  isHighlighted: boolean;
  isNext: boolean;
  onSelect: () => void;
}

const STATUS_BORDER: Record<RaceStatus, string> = {
  completed: "border-l-success/30",
  active: "border-l-live",
  upcoming: "border-l-border-light",
};

const BADGE: Record<RaceStatus, string> = {
  completed: "bg-success/10 text-success/80 border border-success/15",
  active: "bg-live/10 text-live border border-live/20",
  upcoming: "bg-surface-light text-text-muted border border-glass-border",
};

const TYPE_SHORT: Record<string, string> = {
  permanent: "Perm",
  street: "Street",
  hybrid: "Hybrid",
};

export function RaceCard({
  race,
  status,
  nextSession,
  timezone,
  isHighlighted,
  isNext,
  onSelect,
}: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const countdown = useCountdown(nextSession?.dateUtc ?? null);
  const stats = getCircuitStats(race.circuit.id);

  useEffect(() => {
    if (isHighlighted && ref.current) {
      ref.current.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }, [isHighlighted]);

  const raceSession = race.sessions.find((s) => s.name === "Race");

  return (
    <div
      ref={ref}
      onClick={onSelect}
      className={`
        glass-light rounded-xl px-3.5 py-3 cursor-pointer border-l-[3px] card-hover
        ${isNext ? "border-l-highlight" : STATUS_BORDER[status]}
        ${status === "completed" ? "opacity-45" : ""}
        ${status === "active" ? "glow-active" : ""}
        ${isNext ? "ring-1 ring-highlight/25" : isHighlighted && status === "active" ? "ring-1 ring-live/25" : ""}
      `}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-3 min-w-0">
          {/* Flag */}
          <img
            src={getFlagUrl(race.circuit.countryCode)}
            alt={race.circuit.country}
            className="w-9 h-6 rounded object-cover shadow-sm shrink-0"
            loading="lazy"
          />

          <div className="min-w-0">
            {/* Round + Night/Sprint tags */}
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-display font-semibold text-text-muted tracking-widest">
                {race.type === "testing" ? "TEST" : `R${String(race.round).padStart(2, "0")}`}
              </span>
              {stats?.night && (
                <span className="inline-flex items-center gap-0.5 text-[11px] font-bold text-highlight/80 bg-highlight/8 px-1.5 py-0.5 rounded tracking-wider border border-highlight/15">
                  <Moon size={11} />
                  NGT
                </span>
              )}
              {race.type === "sprint" && (
                <span className="inline-flex items-center gap-0.5 text-[11px] font-bold text-warning bg-warning/10 px-1.5 py-0.5 rounded tracking-wider border border-warning/15">
                  <Zap size={11} />
                  SPR
                </span>
              )}
            </div>
            {/* Name */}
            <h3
              className={`font-semibold text-[15px] leading-tight ${
                status === "completed" ? "text-text-muted" : "text-text"
              }`}
            >
              {race.name}
            </h3>
            {/* Location + circuit type */}
            <div className="flex items-center gap-1.5 text-xs text-text-secondary mt-0.5">
              <MapPin size={11} className="shrink-0 text-text-muted" />
              <span>
                {race.circuit.location}
                {stats?.type && (
                  <span className="text-text-muted"> · {TYPE_SHORT[stats.type]}</span>
                )}
              </span>
            </div>
            {/* Active: session progress dots */}
            {status === "active" && (
              <div className="flex items-center gap-1 mt-1">
                {race.sessions.map((s, idx) => (
                  <div
                    key={idx}
                    className={`w-1.5 h-1.5 rounded-full ${
                      new Date(s.dateUtc) < new Date() ? "bg-success" : "bg-border-light"
                    }`}
                  />
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right side: status + time + chevron */}
        <div className="flex items-center gap-2 shrink-0">
          <div className="flex flex-col items-end gap-1.5">
            {isNext ? (
              <span className="text-[11px] font-bold uppercase tracking-wider px-2.5 py-0.5 rounded bg-highlight/15 text-highlight border border-highlight/25 shadow-[0_0_8px_rgba(0,212,255,0.2)]">
                NEXT
              </span>
            ) : (
              <span className={`text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded flex items-center gap-1 ${BADGE[status]}`}>
                {status === "active" && (
                  <span className="inline-block w-1.5 h-1.5 rounded-full bg-live live-dot" />
                )}
                {status === "completed" && <Check size={11} />}
                {status === "active" ? "LIVE" : status}
              </span>
            )}

            {status === "upcoming" && raceSession && (
              <div className="text-right">
                <div className="text-[11px] text-text-muted tabular-nums flex items-center gap-1 justify-end">
                  <Clock size={12} />
                  {formatSessionDate(raceSession.dateUtc, timezone)}
                </div>
                <div className="text-[11px] text-text-secondary tabular-nums">
                  {formatSessionTime(raceSession.dateUtc, timezone)}{" "}
                  <span className="text-text-muted">{getTimezoneAbbr(timezone)}</span>
                </div>
              </div>
            )}

            {status === "active" && countdown && nextSession && (
              <div className="text-right">
                <div className="text-[11px] text-text-muted tracking-wide">{nextSession.name}</div>
                <div className="font-display text-sm font-bold text-live tabular-nums leading-none">
                  {countdown.days > 0 && <><FlipDigit value={String(countdown.days)} />d </>}
                  {countdown.hours > 0 && <><FlipDigit value={String(countdown.hours)} />h </>}
                  <FlipDigit value={String(countdown.minutes).padStart(2, "0")} />:<FlipDigit value={String(countdown.seconds).padStart(2, "0")} />
                </div>
              </div>
            )}
          </div>

          <ChevronRight size={14} className="text-text-muted/30" />
        </div>
      </div>
    </div>
  );
}
