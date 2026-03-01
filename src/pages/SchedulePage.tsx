import { useState, useMemo, useEffect, useRef, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import schedule2026 from "../data/schedule2026";
import { RaceCard } from "../components/RaceCard";
import { RaceDetail } from "../components/RaceDetail";
import { getRaceStatus, getNextSession } from "../utils/time";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface Props {
  timezone: string;
}

const slideVariants = {
  enter: (dir: "left" | "right" | null) => ({
    x: dir === "right" ? 40 : dir === "left" ? -40 : 0,
    opacity: 0,
  }),
  center: { x: 0, opacity: 1 },
  exit: (dir: "left" | "right" | null) => ({
    x: dir === "right" ? -40 : dir === "left" ? 40 : 0,
    opacity: 0,
  }),
};

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

  // Swipe gesture tracking
  const swipeRef = useRef<{ x: number; y: number; t: number } | null>(null);
  const swiped = useRef(false);

  const onPointerDown = useCallback((e: React.PointerEvent) => {
    swipeRef.current = { x: e.clientX, y: e.clientY, t: Date.now() };
    swiped.current = false;
  }, []);

  const onPointerUp = useCallback(
    (e: React.PointerEvent) => {
      if (!swipeRef.current || swiped.current || selectedIndex === null) return;
      const dx = e.clientX - swipeRef.current.x;
      const dy = e.clientY - swipeRef.current.y;
      const dt = Date.now() - swipeRef.current.t;
      swipeRef.current = null;

      // Must be horizontal, fast, and deliberate
      if (dt > 300 || Math.abs(dx) < 40 || Math.abs(dy) > Math.abs(dx) * 0.7) return;

      swiped.current = true;
      if (dx < 0 && selectedIndex < schedule2026.length - 1) {
        setSlideDir("right");
        setSelectedIndex(selectedIndex + 1);
      } else if (dx > 0 && selectedIndex > 0) {
        setSlideDir("left");
        setSelectedIndex(selectedIndex - 1);
      }
    },
    [selectedIndex]
  );

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

    return (
      <motion.div
        className="touch-pan-y"
        initial={{ x: "100%", opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        transition={{ type: "spring", damping: 25, stiffness: 300 }}
        onPointerDown={onPointerDown}
        onPointerUp={onPointerUp}
      >
        {/* Top bar: back + race navigator */}
        <div className="flex items-center justify-between px-3 pt-3">
          <button
            onClick={() => {
              setSelectedIndex(null);
              setSlideDir(null);
            }}
            className="flex items-center gap-1 min-h-10 px-3 text-[13px] font-medium text-text-muted bg-surface-light/50 border border-glass-border rounded-xl active:text-accent active:scale-95 transition-all duration-100"
          >
            <ChevronLeft size={18} />
            Schedule
          </button>

          {/* Prev / indicator / Next */}
          <div className="flex items-center gap-1.5">
            <button
              onClick={goPrev}
              disabled={!hasPrev}
              className="w-10 h-10 flex items-center justify-center rounded-xl bg-surface-light/50 border border-glass-border text-text-muted active:text-accent active:scale-90 transition-all duration-150 disabled:opacity-20"
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
              className="w-10 h-10 flex items-center justify-center rounded-xl bg-surface-light/50 border border-glass-border text-text-muted active:text-accent active:scale-90 transition-all duration-150 disabled:opacity-20"
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </div>

        {/* Race detail — AnimatePresence for swipe slide */}
        <AnimatePresence mode="wait" custom={slideDir}>
          <motion.div
            key={selectedIndex}
            custom={slideDir}
            variants={slideVariants}
            initial="enter"
            animate="center"
            exit="exit"
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
          >
            <RaceDetail race={race} timezone={timezone} globalNextSessionDate={globalNextSessionDate} />
          </motion.div>
        </AnimatePresence>
      </motion.div>
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
        <motion.div
          key={race.round}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: Math.min(i * 0.02, 0.3), duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
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
        </motion.div>
      ))}
    </div>
  );
}
