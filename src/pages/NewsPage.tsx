import { useState, useEffect, useCallback, useRef } from "react";
import { RefreshCw, ExternalLink, AlertCircle, Clock, Check, Tv, Globe, Youtube, Bookmark, Newspaper, MessageCircle, Calendar } from "lucide-react";

interface NewsItem {
  title: string;
  link: string;
  pubDate: string;
  description: string;
  source: string;
}

const RSS_SOURCES = [
  { name: "RACER", url: "https://racer.com/f1/feed/", color: "text-highlight/60" },
  { name: "Autosport", url: "https://www.autosport.com/rss/feed/f1", color: "text-warning/60" },
  { name: "Motorsport", url: "https://www.motorsport.com/rss/f1/news/", color: "text-success/60" },
  { name: "The Race", url: "https://the-race.com/feed/", color: "text-accent/60" },
];

const SOURCE_COLORS: Record<string, string> = Object.fromEntries(
  RSS_SOURCES.map((s) => [s.name, s.color])
);

const QUICK_LINKS = [
  { label: "F1 TV", url: "https://f1tv.formula1.com", icon: Tv, color: "text-highlight" },
  { label: "F1 YouTube", url: "https://www.youtube.com/F1", icon: Youtube, color: "text-live" },
  { label: "Motorsport.com", url: "https://www.motorsport.com/f1/", icon: Newspaper, color: "text-accent" },
  { label: "r/formula1", url: "https://www.reddit.com/r/formula1/", icon: MessageCircle, color: "text-warning" },
  { label: "F1 Calendar", url: "https://f1calendar.com", icon: Calendar, color: "text-success" },
  { label: "FIA Regulations", url: "https://www.fia.com/regulation/category/110", icon: Bookmark, color: "text-text-muted" },
];

function timeAgo(date: Date): string {
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

async function fetchFeed(source: { name: string; url: string }): Promise<NewsItem[]> {
  try {
    const res = await fetch(`https://api.rss2json.com/v1/api.json?rss_url=${encodeURIComponent(source.url)}`);
    const data = await res.json();
    if (data.status !== "ok") return [];
    return (data.items ?? []).map((item: { title: string; link: string; pubDate: string; description: string }) => ({
      ...item,
      source: source.name,
    }));
  } catch {
    return [];
  }
}

export function NewsPage() {
  const [news, setNews] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [toastVisible, setToastVisible] = useState(false);
  const [toastType, setToastType] = useState<"success" | "error">("success");
  const toastTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const showToast = useCallback((type: "success" | "error") => {
    clearTimeout(toastTimer.current);
    setToastType(type);
    setToastVisible(true);
    toastTimer.current = setTimeout(() => setToastVisible(false), 2000);
  }, []);

  const fetchNews = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    const start = Date.now();
    try {
      const results = await Promise.all(RSS_SOURCES.map(fetchFeed));
      const all = results.flat();
      if (all.length === 0) throw new Error("No articles loaded");
      // Dedupe by title (some outlets syndicate the same story)
      const seen = new Set<string>();
      const unique = all.filter((item) => {
        const key = item.title.toLowerCase().trim();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
      unique.sort((a, b) => new Date(b.pubDate).getTime() - new Date(a.pubDate).getTime());
      setNews(unique);
      if (isRefresh) {
        const elapsed = Date.now() - start;
        const delay = Math.max(0, 500 - elapsed);
        setTimeout(() => { setRefreshing(false); showToast("success"); }, delay);
        return;
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load news");
      if (isRefresh) {
        setRefreshing(false);
        showToast("error");
        return;
      }
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { fetchNews(); }, [fetchNews]);

  function stripHtml(html: string): string {
    const tmp = document.createElement("div");
    tmp.innerHTML = html;
    return tmp.textContent ?? tmp.innerText ?? "";
  }

  return (
    <div className="px-3 pt-3 pb-2 space-y-2.5 max-w-2xl mx-auto">
      {/* Toast */}
      <div
        className={`fixed top-40 left-1/2 -translate-x-1/2 z-50 transition-all duration-300 ${
          toastVisible ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-3 pointer-events-none"
        }`}
      >
        <div className={`rounded-lg px-4 py-2 flex items-center gap-2 shadow-lg backdrop-blur-md ${
          toastType === "success"
            ? "bg-surface/90 text-success border border-success/30"
            : "bg-surface/90 text-live border border-live/30"
        }`}>
          {toastType === "success" ? (
            <Check size={14} strokeWidth={3} />
          ) : (
            <AlertCircle size={14} />
          )}
          <span className="text-xs font-bold tracking-wide">
            {toastType === "success" ? "News updated" : "Refresh failed"}
          </span>
        </div>
      </div>

      {/* Quick Links */}
      <div className="space-y-2">
        <h3 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase px-1">
          Quick Links
        </h3>
        <div className="grid grid-cols-3 gap-2">
          {QUICK_LINKS.map((link) => {
            const Icon = link.icon;
            return (
              <a
                key={link.label}
                href={link.url}
                target="_blank"
                rel="noopener noreferrer"
                className="glass-light rounded-xl px-3 py-2.5 flex items-center gap-2.5 card-hover active:border-accent/30 border border-transparent"
              >
                <Icon size={16} className={`${link.color} shrink-0`} />
                <div>
                  <span className="text-[13px] font-medium text-text">{link.label}</span>
                  <ExternalLink size={9} className="text-text-muted/40 inline ml-1" />
                </div>
              </a>
            );
          })}
        </div>
      </div>

      {/* Section header */}
      <div className="flex items-center justify-between px-1">
        <div className="flex items-center gap-2">
          <h2 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase">
            Latest News
          </h2>
          {!loading && !error && news.length > 0 && (
            <span className="text-[10px] text-text-muted/60 tabular-nums">{news.length} articles</span>
          )}
        </div>
        <button
          onClick={() => fetchNews(true)}
          disabled={refreshing}
          className={`p-2 rounded-lg transition-all duration-150 disabled:opacity-40 ${
            refreshing
              ? "text-accent bg-accent/10"
              : "text-text-muted active:text-accent active:bg-surface-light"
          }`}
        >
          <RefreshCw size={16} className={`transition-transform duration-300 ${refreshing ? "animate-spin" : ""}`} />
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="skeleton h-24 rounded-xl" />
          ))}
        </div>
      )}

      {/* Error */}
      {error && !loading && (
        <div className="glass rounded-xl p-6 text-center space-y-3">
          <AlertCircle size={28} className="mx-auto text-live" />
          <p className="text-sm text-text-secondary">{error}</p>
          <button onClick={() => fetchNews()} className="inline-flex items-center gap-1.5 text-xs font-semibold text-accent">
            <RefreshCw size={12} /> Retry
          </button>
        </div>
      )}

      {/* Articles */}
      <div className={`space-y-2 transition-opacity duration-200 ${refreshing ? "opacity-40" : "opacity-100"}`}>
      {!loading && !error && news.map((item, i) => {
        const date = new Date(item.pubDate);
        const excerpt = stripHtml(item.description).slice(0, 140);
        const isFeatured = i === 0;
        const sourceColor = SOURCE_COLORS[item.source] ?? "text-text-muted";

        return (
          <a
            key={`${item.source}-${i}`}
            href={item.link}
            target="_blank"
            rel="noopener noreferrer"
            className={`stagger-item glass-light rounded-xl p-3.5 block space-y-1.5 card-hover border-l-[3px] group ${
              isFeatured
                ? "border-l-accent bg-accent/5"
                : "border-l-highlight/30 active:border-l-accent"
            }`}
            style={{ animationDelay: `${Math.min(i * 25, 200)}ms` }}
          >
            {isFeatured && (
              <span className="inline-block text-[10px] font-bold text-accent uppercase tracking-widest mb-0.5">
                Latest
              </span>
            )}
            <h3 className={`font-semibold text-text leading-snug group-active:text-accent transition-colors duration-100 line-clamp-2 ${
              isFeatured ? "text-[15px]" : "text-sm"
            }`}>
              {item.title}
            </h3>

            <div className="flex items-center gap-1.5 text-[11px] text-text-muted">
              <Clock size={11} />
              <span>{timeAgo(date)}</span>
              <span className="text-border-light">·</span>
              <span className={`font-semibold uppercase tracking-wider text-[10px] ${sourceColor}`}>{item.source}</span>
            </div>

            {excerpt && (
              <p className="text-[13px] text-text-secondary leading-relaxed line-clamp-2">{excerpt}</p>
            )}

            <span className="inline-flex items-center gap-1 text-[11px] text-highlight font-medium tracking-wide">
              Read more <ExternalLink size={11} />
            </span>
          </a>
        );
      })}
      </div>
    </div>
  );
}
