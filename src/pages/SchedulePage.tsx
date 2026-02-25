import { useState, useMemo, useEffect } from "react";
import schedule2026 from "../data/schedule2026";
import { RaceCard } from "../components/RaceCard";
import { RaceDetail } from "../components/RaceDetail";
import { getRaceStatus, getNextSession } from "../utils/time";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface Props {
  timezone: string;
}

export function SchedulePage({ timezone }: Props) {
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [slideDir, setSlideDir] = useState<"left" | "right" | null>(null);

  // Tick every 30s so race statuses update live (upcoming → active → completed)
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 30_000);
    return () => clearInterval(id);
  }, []);

  const racesWithStatus = useMemo(
    () =>
      schedule2026.map((race) => ({
        race,
        status: getRaceStatus(race),
        nextSession: getNextSession(race),
      })),
    [tick]
  );

  // The one globally-next session across all races
  const globalNextSessionDate = useMemo(() => {
    for (const race of schedule2026) {
      const ns = getNextSession(race);
      if (ns) return ns.dateUtc;
    }
    return null;
  }, [tick]);

  const highlightedRound = useMemo(() => {
    const active = racesWithStatus.find((r) => r.status === "active");
    if (active) return active.race.round;
    const upcoming = racesWithStatus.find((r) => r.status === "upcoming");
    if (upcoming) return upcoming.race.round;
    return racesWithStatus[racesWithStatus.length - 1]?.race.round ?? 0;
  }, [racesWithStatus]);

  if (selectedIndex !== null) {
    const race = schedule2026[selectedIndex];
    const hasPrev = selectedIndex > 0;
    const hasNext = selectedIndex < schedule2026.length - 1;

    const goPrev = () => {
      if (hasPrev) {
        setSlideDir("left");
        setSelectedIndex(selectedIndex - 1);
      }
    };

    const goNext = () => {
      if (hasNext) {
        setSlideDir("right");
        setSelectedIndex(selectedIndex + 1);
      }
    };

    const slideClass =
      slideDir === "right"
        ? "slide-from-right"
        : slideDir === "left"
          ? "slide-from-left"
          : "page-enter";

    return (
      <div className="page-enter">
        {/* Top bar: back + race navigator */}
        <div className="flex items-center justify-between px-3 pt-3">
          <button
            onClick={() => {
              setSelectedIndex(null);
              setSlideDir(null);
            }}
            className="flex items-center gap-1 min-h-11 px-2 -ml-2 text-[13px] font-medium text-text-muted hover:text-accent transition-colors duration-100"
          >
            <ChevronLeft size={18} />
            Schedule
          </button>

          {/* Prev / indicator / Next */}
          <div className="flex items-center gap-1.5">
            <button
              onClick={goPrev}
              disabled={!hasPrev}
              className="w-10 h-10 flex items-center justify-center rounded-xl bg-surface-light/50 border border-glass-border text-text-muted hover:text-accent hover:border-accent/30 active:scale-90 transition-all duration-150 disabled:opacity-20 disabled:hover:text-text-muted disabled:hover:border-glass-border"
            >
              <ChevronLeft size={18} />
            </button>
            <span className="text-[11px] font-display font-semibold text-text-muted tracking-widest min-w-[3.5ch] text-center">
              {race.type === "testing"
                ? "TEST"
                : `R${String(race.round).padStart(2, "0")}`}
            </span>
            <button
              onClick={goNext}
              disabled={!hasNext}
              className="w-10 h-10 flex items-center justify-center rounded-xl bg-surface-light/50 border border-glass-border text-text-muted hover:text-accent hover:border-accent/30 active:scale-90 transition-all duration-150 disabled:opacity-20 disabled:hover:text-text-muted disabled:hover:border-glass-border"
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </div>

        {/* Race detail — key forces remount for slide animation */}
        <div key={selectedIndex} className={slideClass}>
          <RaceDetail race={race} timezone={timezone} globalNextSessionDate={globalNextSessionDate} />
        </div>
      </div>
    );
  }

  return (
    <div className="px-3 pt-3 pb-2 space-y-2">
      {/* Legend */}
      <div className="flex items-center gap-4 px-1 pb-1 text-[11px] text-text-muted tracking-wider uppercase font-medium">
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-success" />
          Done
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-live live-dot" />
          Live
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-highlight" />
          Next
        </span>
        <span className="ml-auto text-text-muted/50">{racesWithStatus.length} races</span>
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
            isNext={race.round === highlightedRound && status === "upcoming"}
            onSelect={() => {
              setSlideDir(null);
              setSelectedIndex(i);
            }}
          />
        </div>
      ))}
    </div>
  );
}
