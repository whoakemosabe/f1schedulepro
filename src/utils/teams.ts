export const TEAM_COLORS: Record<string, string> = {
  red_bull: "#3671C6",
  ferrari: "#E8002D",
  mercedes: "#27F4D2",
  mclaren: "#FF8000",
  aston_martin: "#229971",
  alpine: "#FF87BC",
  williams: "#64C4FF",
  sauber: "#52E252",
  haas: "#B6BABD",
  rb: "#6692FF",
};

export function getTeamColor(teamId: string): string {
  return TEAM_COLORS[teamId] ?? "#4db8ff";
}

const TEAM_SLUGS: Record<string, string> = {
  red_bull: "red-bull-racing",
  ferrari: "ferrari",
  mercedes: "mercedes",
  mclaren: "mclaren",
  aston_martin: "aston-martin",
  alpine: "alpine",
  williams: "williams",
  sauber: "kick-sauber",
  haas: "haas",
  rb: "racing-bulls",
};

export function getTeamUrl(teamId: string): string | null {
  const slug = TEAM_SLUGS[teamId];
  return slug ? `https://www.formula1.com/en/teams/${slug}` : null;
}

export function hexToRgba(hex: string, alpha: number): string {
  const clean = hex.replace("#", "");
  const r = parseInt(clean.slice(0, 2), 16);
  const g = parseInt(clean.slice(2, 4), 16);
  const b = parseInt(clean.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}
