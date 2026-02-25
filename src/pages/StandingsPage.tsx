import { useState, useEffect, useCallback } from "react";
import { getTeamColor } from "../utils/teams";
import { getCountryCode, getFlagUrl } from "../utils/countries";
import { Trophy, Users, RefreshCw, AlertCircle } from "lucide-react";

interface Driver {
  position: number;
  points: number;
  driver: {
    name: string;
    surname: string;
    shortName?: string;
    nationality: string;
  };
  team: {
    teamId: string;
    teamName: string;
  };
}

export function StandingsPage() {
  const [view, setView] = useState<"drivers" | "constructors">("drivers");
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStandings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("https://f1api.dev/api/current/drivers-championship");
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setDrivers(data.drivers_championship ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load standings");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchStandings(); }, [fetchStandings]);

  const constructors = drivers.reduce(
    (acc, d) => {
      const id = d.team?.teamId ?? "unknown";
      if (!acc[id]) {
        acc[id] = { name: d.team?.teamName ?? "Unknown", color: getTeamColor(id), drivers: [], points: 0 };
      }
      acc[id].drivers.push(d);
      acc[id].points += d.points;
      return acc;
    },
    {} as Record<string, { name: string; color: string; drivers: Driver[]; points: number }>
  );

  const sortedConstructors = Object.entries(constructors).sort(([, a], [, b]) => b.points - a.points);
  const leaderPts = drivers.length > 0 ? drivers[0].points : 0;

  return (
    <div className="px-2.5 pt-2.5 space-y-2 max-w-2xl mx-auto">
      {/* Toggle */}
      <div className="glass rounded-lg p-0.5 flex">
        {(["drivers", "constructors"] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            className={`flex-1 flex items-center justify-center gap-1 py-1.5 rounded-md text-[11px] font-semibold uppercase tracking-wider transition-all duration-150 ${
              view === v
                ? "bg-accent text-white shadow-md shadow-accent/20"
                : "text-text-muted hover:text-text-secondary"
            }`}
          >
            {v === "drivers" ? <Trophy size={11} /> : <Users size={11} />}
            {v}
          </button>
        ))}
      </div>

      {/* Loading */}
      {loading && (
        <div className="space-y-1.5">
          {Array.from({ length: 10 }).map((_, i) => (
            <div key={i} className="skeleton h-12 rounded-lg" />
          ))}
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="glass rounded-xl p-5 text-center space-y-2">
          <AlertCircle size={24} className="mx-auto text-live" />
          <p className="text-xs text-text-secondary">{error}</p>
          <button onClick={fetchStandings} className="inline-flex items-center gap-1 text-[10px] font-semibold text-accent">
            <RefreshCw size={10} /> Retry
          </button>
        </div>
      )}

      {/* Drivers */}
      {!loading && !error && view === "drivers" && (
        <div className="space-y-1">
          {drivers.map((d, i) => {
            const color = getTeamColor(d.team?.teamId ?? "");
            const cc = getCountryCode(d.driver.nationality);
            const behind = d.points < leaderPts ? leaderPts - d.points : 0;

            return (
              <div
                key={d.driver.surname}
                className="stagger-item glass-light rounded-lg px-2 py-1.5 flex items-center gap-2 border-l-2 card-hover"
                style={{
                  borderLeftColor: color,
                  animationDelay: `${Math.min(i * 15, 200)}ms`,
                }}
              >
                <span className="font-display text-[13px] font-bold text-text-muted w-5 text-center tabular-nums shrink-0">
                  {d.position}
                </span>
                <img src={getFlagUrl(cc)} alt="" className="w-5 h-3.5 rounded-sm object-cover shrink-0" loading="lazy" />
                <div className="flex-1 min-w-0">
                  <p className="text-[12px] font-semibold text-text truncate">
                    {d.driver.name} <span className="text-text-secondary font-normal">{d.driver.surname}</span>
                  </p>
                  <p className="text-[9px] font-medium uppercase tracking-wider truncate" style={{ color }}>{d.team?.teamName}</p>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-display text-[12px] font-bold text-text tabular-nums">{d.points}</span>
                  <span className="text-[8px] text-text-muted ml-px">pts</span>
                  {behind > 0 && <p className="text-[8px] text-text-muted tabular-nums">-{behind}</p>}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Constructors */}
      {!loading && !error && view === "constructors" && (
        <div className="space-y-1.5">
          {sortedConstructors.map(([id, team], idx) => {
            const leaderTeamPts = sortedConstructors[0]?.[1].points ?? 0;
            const behind = team.points < leaderTeamPts ? leaderTeamPts - team.points : 0;

            return (
              <div
                key={id}
                className="stagger-item glass-light rounded-lg overflow-hidden border-l-2"
                style={{ borderLeftColor: team.color, animationDelay: `${idx * 30}ms` }}
              >
                <div className="px-2.5 py-1.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="font-display text-[13px] font-bold text-text-muted tabular-nums">{idx + 1}</span>
                    <span className="text-[12px] font-bold uppercase tracking-wide" style={{ color: team.color }}>{team.name}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="font-display text-[12px] font-bold text-text tabular-nums">{team.points}</span>
                    <span className="text-[8px] text-text-muted">pts</span>
                    {behind > 0 && <span className="text-[8px] text-text-muted tabular-nums">-{behind}</span>}
                  </div>
                </div>
                <div className="border-t border-glass-border divide-y divide-glass-border">
                  {team.drivers.map((d) => (
                    <div key={d.driver.surname} className="px-2.5 py-1 flex items-center justify-between text-[11px]">
                      <span className="text-text-secondary">{d.driver.name} {d.driver.surname}</span>
                      <span className="font-semibold text-text tabular-nums">{d.points}</span>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
