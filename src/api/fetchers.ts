// ── News ──

export interface NewsItem {
  title: string;
  link: string;
  pubDate: string;
  description: string;
  source: string;
}

const RSS_SOURCES = [
  { name: "RACER", url: "https://racer.com/f1/feed/" },
  { name: "Autosport", url: "https://www.autosport.com/rss/feed/f1" },
  { name: "Motorsport", url: "https://www.motorsport.com/rss/f1/news/" },
  { name: "The Race", url: "https://the-race.com/feed/" },
];

export const SOURCE_COLORS: Record<string, string> = {
  RACER: "text-highlight/60",
  Autosport: "text-warning/60",
  Motorsport: "text-success/60",
  "The Race": "text-accent/60",
};

async function fetchFeed(source: { name: string; url: string }): Promise<NewsItem[]> {
  try {
    const res = await fetch(
      `https://api.rss2json.com/v1/api.json?rss_url=${encodeURIComponent(source.url)}`
    );
    const data = await res.json();
    if (data.status !== "ok") return [];
    return (data.items ?? []).map(
      (item: { title: string; link: string; pubDate: string; description: string }) => ({
        ...item,
        source: source.name,
      })
    );
  } catch {
    return [];
  }
}

export async function fetchNews(): Promise<NewsItem[]> {
  const results = await Promise.all(RSS_SOURCES.map(fetchFeed));
  const all = results.flat();
  if (all.length === 0) throw new Error("No articles loaded");
  const seen = new Set<string>();
  const unique = all.filter((item) => {
    const key = item.title.toLowerCase().trim();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  unique.sort((a, b) => new Date(b.pubDate).getTime() - new Date(a.pubDate).getTime());
  return unique;
}

// ── Standings ──

export interface Driver {
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

export async function fetchDriverStandings(): Promise<Driver[]> {
  const res = await fetch("https://f1api.dev/api/current/drivers-championship");
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const data = await res.json();
  return data.drivers_championship ?? [];
}
