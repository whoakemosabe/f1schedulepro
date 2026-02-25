import { useState, useMemo } from "react";
import schedule2026 from "../data/schedule2026";
import { RaceCard } from "../components/RaceCard";
import { RaceDetail } from "../components/RaceDetail";
import { getRaceStatus, getNextSession } from "../utils/time";
import type { Race } from "../types";
import { ChevronLeft } from "lucide-react";

interface Props {
  timezone: string;
}

export function SchedulePage({ timezone }: Props) {
  const [selectedRace, setSelectedRace] = useState<Race | null>(null);

  const racesWithStatus = useMemo(
    () =>
      schedule2026.map((race) => ({
        race,
        status: getRaceStatus(race),
        nextSession: getNextSession(race),
      })),
    []
  );

  const highlightedRound = useMemo(() => {
    const active = racesWithStatus.find((r) => r.status === "active");
    if (active) return active.race.round;
    const upcoming = racesWithStatus.find((r) => r.status === "upcoming");
    if (upcoming) return upcoming.race.round;
    return racesWithStatus[racesWithStatus.length - 1]?.race.round ?? 0;
  }, [racesWithStatus]);

  if (selectedRace) {
    return (
      <div className="page-enter">
        <button
          onClick={() => setSelectedRace(null)}
          className="flex items-center gap-0.5 px-3 pt-3 text-[11px] font-medium text-text-muted hover:text-accent transition-colors duration-100"
        >
          <ChevronLeft size={14} />
          Schedule
        </button>
        <RaceDetail race={selectedRace} timezone={timezone} />
      </div>
    );
  }

  return (
    <div className="px-2.5 pt-2.5 pb-2 space-y-1.5">
      {/* Legend */}
      <div className="flex items-center gap-3 px-1 pb-1 text-[9px] text-text-muted tracking-wider uppercase font-medium">
        <span className="flex items-center gap-1">
          <span className="w-1.5 h-1.5 rounded-full bg-success" />
          Done
        </span>
        <span className="flex items-center gap-1">
          <span className="w-1.5 h-1.5 rounded-full bg-live live-dot" />
          Live
        </span>
        <span className="flex items-center gap-1">
          <span className="w-1.5 h-1.5 rounded-full bg-highlight" />
          Next
        </span>
        <span className="ml-auto text-text-muted/40">{racesWithStatus.length} races</span>
      </div>

      {racesWithStatus.map(({ race, status, nextSession }, i) => (
        <div
          key={race.round}
          className="stagger-item"
          style={{ animationDelay: `${Math.min(i * 20, 300)}ms` }}
        >
          <RaceCard
            race={race}
            status={status}
            nextSession={nextSession}
            timezone={timezone}
            isHighlighted={race.round === highlightedRound}
            onSelect={() => setSelectedRace(race)}
          />
        </div>
      ))}
    </div>
  );
}
