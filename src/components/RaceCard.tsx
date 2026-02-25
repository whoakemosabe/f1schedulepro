import { useRef, useEffect } from "react";
import type { Race, RaceStatus, Session } from "../types";
import { formatSessionDate, formatSessionTime, getTimezoneAbbr } from "../utils/time";
import { useCountdown } from "../hooks/useCountdown";
import { getFlagUrl } from "../utils/countries";
import { MapPin, Clock, Zap } from "lucide-react";

interface Props {
  race: Race;
  status: RaceStatus;
  nextSession: Session | null;
  timezone: string;
  isHighlighted: boolean;
  onSelect: () => void;
}

const STATUS_BORDER: Record<RaceStatus, string> = {
  completed: "border-l-success/30",
  active: "border-l-live",
  upcoming: "border-l-highlight/60",
};

const BADGE: Record<RaceStatus, string> = {
  completed: "bg-success/10 text-success/80 border border-success/15",
  active: "bg-live/10 text-live border border-live/20",
  upcoming: "bg-highlight/8 text-highlight/80 border border-highlight/12",
};

export function RaceCard({
  race,
  status,
  nextSession,
  timezone,
  isHighlighted,
  onSelect,
}: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const countdown = useCountdown(nextSession?.dateUtc ?? null);

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
        glass-light rounded-lg px-2.5 py-2 cursor-pointer border-l-2 card-hover
        ${STATUS_BORDER[status]}
        ${status === "completed" ? "opacity-50" : ""}
        ${status === "active" ? "glow-active" : ""}
        ${isHighlighted ? "ring-1 ring-accent/20" : ""}
      `}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          {/* Flag */}
          <img
            src={getFlagUrl(race.circuit.countryCode)}
            alt={race.circuit.country}
            className="w-7 h-5 rounded-[3px] object-cover shadow-sm shrink-0"
            loading="lazy"
          />

          <div className="min-w-0">
            {/* Round + Sprint tag */}
            <div className="flex items-center gap-1.5">
              <span className="text-[9px] font-display font-semibold text-text-muted tracking-widest">
                {race.type === "testing" ? "TEST" : `R${String(race.round).padStart(2, "0")}`}
              </span>
              {race.type === "sprint" && (
                <span className="inline-flex items-center gap-px text-[8px] font-bold text-warning bg-warning/8 px-1 py-px rounded tracking-wider border border-warning/15">
                  <Zap size={7} />
                  SPR
                </span>
              )}
            </div>
            {/* Name */}
            <h3
              className={`font-semibold text-[13px] leading-tight truncate ${
                status === "completed" ? "line-through text-text-muted" : "text-text"
              }`}
            >
              {race.name}
            </h3>
            {/* Location */}
            <div className="flex items-center gap-1 text-[10px] text-text-muted mt-px">
              <MapPin size={8} className="shrink-0" />
              <span className="truncate">{race.circuit.location}</span>
            </div>
          </div>
        </div>

        {/* Right side: status + time */}
        <div className="flex flex-col items-end shrink-0 gap-1">
          <span className={`text-[8px] font-bold uppercase tracking-wider px-1.5 py-px rounded ${BADGE[status]}`}>
            {status === "active" && (
              <span className="inline-block w-1 h-1 rounded-full bg-live live-dot mr-0.5 align-middle" />
            )}
            {status}
          </span>

          {status === "upcoming" && raceSession && (
            <div className="text-right">
              <div className="text-[9px] text-text-muted tabular-nums flex items-center gap-0.5 justify-end">
                <Clock size={7} />
                {formatSessionDate(raceSession.dateUtc, timezone)}
              </div>
              <div className="text-[9px] text-text-secondary tabular-nums">
                {formatSessionTime(raceSession.dateUtc, timezone)}{" "}
                <span className="text-text-muted">{getTimezoneAbbr(timezone)}</span>
              </div>
            </div>
          )}

          {status === "active" && countdown && nextSession && (
            <div className="text-right">
              <div className="text-[8px] text-text-muted tracking-wide">{nextSession.name}</div>
              <div className="font-display text-[11px] font-bold text-accent tabular-nums leading-none">
                {countdown.days > 0 && `${countdown.days}d `}
                {countdown.hours > 0 && `${countdown.hours}h `}
                {String(countdown.minutes).padStart(2, "0")}:{String(countdown.seconds).padStart(2, "0")}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
