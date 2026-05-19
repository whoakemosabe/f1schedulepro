import { useState, useEffect, useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getTeamColor, getTeamUrl } from "../utils/teams";
import { getCountryCode, getFlagUrl } from "../utils/countries";
import { Trophy, Users, RefreshCw, AlertCircle, Info, ExternalLink } from "lucide-react";
import { fetchDriverStandings } from "../api/fetchers";

const FIRST_RACE_2026 = new Date("2026-03-08T04:00:00Z");

interface Props {
  isActive: boolean;
  hasBeenActive: boolean;
}

export function StandingsPage({ isActive, hasBeenActive }: Props) {
  useEffect(() => {
    if (isActive) window.scrollTo({ top: 0, behavior: "instant" });
  }, [isActive]);

  const [view, setView] = useState<"drivers" | "constructors">("drivers");

  const queryClient = useQueryClient();
  const { data: drivers = [], isLoading, error } = useQuery({
    queryKey: ["standings"],
    queryFn: fetchDriverStandings,
    staleTime: 10 * 60 * 1000,
    enabled: hasBeenActive,
  });

  const isPreSeason = new Date() < FIRST_RACE_2026;

  const constructors = useMemo(() => drivers.reduce(
    (acc, d) => {
      const id = d.team?.teamId ?? "unknown";
      if (!acc[id]) {
        acc[id] = { name: d.team?.teamName ?? "Unknown", color: getTeamColor(id), drivers: [], points: 0 };
      }
      acc[id].drivers.push(d);
      acc[id].points += d.points;
      return acc;
    },
    {} as Record<string, { name: string; color: string; drivers: typeof drivers; points: number }>
  ), [drivers]);

  const sortedConstructors = Object.entries(constructors).sort(([, a], [, b]) => b.points - a.points);
  const leaderPts = drivers.length > 0 ? drivers[0].points : 0;

  return (
    <div className="px-3 pt-3 pb-2 space-y-2.5 max-w-2xl mx-auto">
      {/* 2025 season banner */}
      {isPreSeason && !isLoading && !error && drivers.length > 0 && (
        <div className="season-banner glass rounded-xl px-4 py-3 flex items-start gap-3 border border-warning/20 bg-warning/5">
          <Info size={18} className="text-warning shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-warning">2025 Season Standings</p>
            <p className="text-xs text-text-secondary mt-0.5">
              The 2026 season begins March 8 in Melbourne. Standings will update automatically.
            </p>
          </div>
        </div>
      )}

      {/* Toggle with sliding indicator */}
      <div className="glass rounded-xl p-1 flex relative overflow-hidden">
        {/* Sliding pill */}
        <div
          className="absolute top-1 bottom-1 rounded-lg bg-accent shadow-md shadow-accent/20"
          style={{
            width: "calc(50% - 4px)",
            left: view === "drivers" ? "4px" : "50%",
            transition: "left 0.35s cubic-bezier(0.34, 1.56, 0.64, 1)",
          }}
        />
        {(["drivers", "constructors"] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            className={`relative z-10 flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-[13px] font-semibold uppercase tracking-wider transition-colors duration-200 ${
              view === v
                ? "text-white"
                : "text-text-muted active:text-text-secondary"
            }`}
          >
            {v === "drivers" ? <Trophy size={14} /> : <Users size={14} />}
            {v}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 10 }).map((_, i) => (
            <div key={i} className="skeleton h-14 rounded-xl" />
          ))}
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="glass rounded-xl p-6 text-center space-y-3">
          <AlertCircle size={28} className="mx-auto text-live" />
          <p className="text-sm text-text-secondary">{error instanceof Error ? error.message : "Failed to load standings"}</p>
          <button onClick={() => queryClient.invalidateQueries({ queryKey: ["standings"] })} className="inline-flex items-center gap-1.5 text-xs font-semibold text-accent">
            <RefreshCw size={12} /> Retry
          </button>
        </div>
      )}

      {/* Drivers */}
      {!isLoading && !error && view === "drivers" && (
        <div className="space-y-1.5">
          {drivers.map((d, i) => {
            const color = getTeamColor(d.team?.teamId ?? "");
            const cc = getCountryCode(d.driver.nationality);
            const behind = d.points < leaderPts ? leaderPts - d.points : 0;

            return (
              <div
                key={d.driver.surname}
                className="stagger-item glass-light rounded-xl px-3 py-2.5 flex items-center gap-3 border-l-4 card-hover"
                style={{ borderLeftColor: color, animationDelay: `${Math.min(i * 15, 200)}ms` }}
              >
                <span className="font-display text-[15px] font-bold text-text-muted w-6 text-center tabular-nums shrink-0">
                  {d.position}
                </span>
                <img src={getFlagUrl(cc)} alt="" className="w-6 h-4 rounded-sm object-cover shrink-0" loading="lazy" />
                <div className="flex-1 min-w-0">
                  <a
                    href={`https://www.formula1.com/en/drivers/${d.driver.name}-${d.driver.surname}`.toLowerCase().replace(/\s+/g, "-")}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-sm font-semibold text-text truncate inline-flex items-center gap-1 active:opacity-70 transition-opacity duration-100"
                  >
                    {d.driver.name} <span className="text-text-secondary font-normal">{d.driver.surname}</span>
                    <ExternalLink size={10} className="text-text-muted shrink-0" />
                  </a>
                  <a
                    href={getTeamUrl(d.team?.teamId ?? "") ?? "#"}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[11px] font-medium uppercase tracking-wider truncate flex items-center gap-1 active:opacity-70 transition-opacity duration-100"
                    style={{ color }}
                  >{d.team?.teamName} <ExternalLink size={8} className="text-text-muted shrink-0" /></a>
                </div>
                <div className="text-right shrink-0">
                  <span className="font-display text-sm font-bold text-text tabular-nums">{d.points}</span>
                  <span className="text-[11px] text-text-muted ml-0.5">pts</span>
                  {behind > 0 && <p className="text-[11px] text-text-muted tabular-nums">-{behind}</p>}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Constructors */}
      {!isLoading && !error && view === "constructors" && (
        <div className="space-y-2">
          {sortedConstructors.map(([id, team], idx) => {
            const leaderTeamPts = sortedConstructors[0]?.[1].points ?? 0;
            const behind = team.points < leaderTeamPts ? leaderTeamPts - team.points : 0;

            return (
              <div
                key={id}
                className="stagger-item glass-light rounded-xl overflow-hidden border-l-4"
                style={{ borderLeftColor: team.color, animationDelay: `${Math.min(idx * 30, 200)}ms` }}
              >
                <div className="px-3.5 py-2.5 flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="font-display text-[15px] font-bold text-text-muted tabular-nums">{idx + 1}</span>
                    <a
                      href={getTeamUrl(id) ?? "#"}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm font-bold uppercase tracking-wide inline-flex items-center gap-1 active:opacity-70 transition-opacity duration-100"
                      style={{ color: team.color }}
                    >{team.name} <ExternalLink size={10} className="text-text-muted shrink-0" /></a>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="font-display text-sm font-bold text-text tabular-nums">{team.points}</span>
                    <span className="text-[11px] text-text-muted">pts</span>
                    {behind > 0 && <span className="text-[11px] text-text-muted tabular-nums">-{behind}</span>}
                  </div>
                </div>
                <div className="border-t border-glass-border divide-y divide-glass-border">
                  {team.drivers.map((d) => (
                    <div key={d.driver.surname} className="px-3.5 py-1.5 flex items-center justify-between text-[13px]">
                      <a
                        href={`https://www.formula1.com/en/drivers/${d.driver.name}-${d.driver.surname}`.toLowerCase().replace(/\s+/g, "-")}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-text-secondary inline-flex items-center gap-1 active:opacity-70 transition-opacity duration-100"
                      >{d.driver.name} {d.driver.surname} <ExternalLink size={9} className="text-text-muted shrink-0" /></a>
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
