import { useState, useEffect, useCallback } from "react";
import { RefreshCw, ExternalLink, AlertCircle, Calendar } from "lucide-react";

interface NewsItem {
  title: string;
  link: string;
  pubDate: string;
  description: string;
}

export function NewsPage() {
  const [news, setNews] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const fetchNews = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const res = await fetch("https://api.rss2json.com/v1/api.json?rss_url=https%3A%2F%2Fracer.com%2Ff1%2Ffeed%2F");
      const data = await res.json();
      if (data.status !== "ok") throw new Error(data.message ?? "Feed error");
      setNews(data.items ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load news");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { fetchNews(); }, [fetchNews]);

  function stripHtml(html: string): string {
    const tmp = document.createElement("div");
    tmp.innerHTML = html;
    return tmp.textContent ?? tmp.innerText ?? "";
  }

  return (
    <div className="px-2.5 pt-2.5 space-y-1.5 max-w-2xl mx-auto">
      {/* Section header */}
      <div className="flex items-center justify-between px-0.5">
        <h2 className="font-display text-[10px] font-semibold tracking-widest text-text-muted uppercase">
          Latest News
        </h2>
        <button
          onClick={() => fetchNews(true)}
          disabled={refreshing}
          className="p-1.5 rounded-md text-text-muted hover:text-accent hover:bg-surface-light transition-all duration-100 disabled:opacity-40"
        >
          <RefreshCw size={12} className={refreshing ? "animate-spin" : ""} />
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div className="space-y-1.5">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="skeleton h-20 rounded-lg" />
          ))}
        </div>
      )}

      {/* Error */}
      {error && !loading && (
        <div className="glass rounded-xl p-5 text-center space-y-2">
          <AlertCircle size={24} className="mx-auto text-live" />
          <p className="text-xs text-text-secondary">{error}</p>
          <button onClick={() => fetchNews()} className="inline-flex items-center gap-1 text-[10px] font-semibold text-accent">
            <RefreshCw size={10} /> Retry
          </button>
        </div>
      )}

      {/* Articles */}
      {!loading && !error && news.map((item, i) => {
        const date = new Date(item.pubDate);
        const excerpt = stripHtml(item.description).slice(0, 140);

        return (
          <a
            key={i}
            href={item.link}
            target="_blank"
            rel="noopener noreferrer"
            className="stagger-item glass-light rounded-lg p-2.5 block space-y-1 card-hover border-l-2 border-l-transparent hover:border-l-accent group"
            style={{ animationDelay: `${Math.min(i * 25, 200)}ms` }}
          >
            <h3 className="text-[12px] font-semibold text-text leading-snug group-hover:text-accent transition-colors duration-100 line-clamp-2">
              {item.title}
            </h3>

            <div className="flex items-center gap-1 text-[9px] text-text-muted">
              <Calendar size={8} />
              {date.toLocaleDateString("en-US", { month: "short", day: "numeric" })}
              {" · "}
              {date.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })}
            </div>

            {excerpt && (
              <p className="text-[11px] text-text-secondary leading-relaxed line-clamp-2">{excerpt}</p>
            )}

            <span className="inline-flex items-center gap-0.5 text-[9px] text-highlight font-medium tracking-wide">
              Read more <ExternalLink size={7} />
            </span>
          </a>
        );
      })}
    </div>
  );
}
