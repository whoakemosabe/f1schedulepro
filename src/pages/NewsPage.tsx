import { useState, useEffect, useCallback, useRef } from "react";
import { RefreshCw, ExternalLink, AlertCircle, Calendar, Check } from "lucide-react";

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
      const res = await fetch("https://api.rss2json.com/v1/api.json?rss_url=https%3A%2F%2Fracer.com%2Ff1%2Ffeed%2F");
      const data = await res.json();
      if (data.status !== "ok") throw new Error(data.message ?? "Feed error");
      setNews(data.items ?? []);
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
    <div className="px-3 pt-3 pb-2 space-y-2 max-w-2xl mx-auto">
      {/* Toast */}
      <div
        className={`fixed top-16 left-1/2 -translate-x-1/2 z-50 transition-all duration-300 ${
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

      {/* Section header */}
      <div className="flex items-center justify-between px-1">
        <h2 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase">
          Latest News
        </h2>
        <button
          onClick={() => fetchNews(true)}
          disabled={refreshing}
          className={`p-2 rounded-lg transition-all duration-150 disabled:opacity-40 ${
            refreshing
              ? "text-accent bg-accent/10"
              : "text-text-muted hover:text-accent hover:bg-surface-light"
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

        return (
          <a
            key={i}
            href={item.link}
            target="_blank"
            rel="noopener noreferrer"
            className="stagger-item glass-light rounded-xl p-3.5 block space-y-1.5 card-hover border-l-[3px] border-l-highlight/30 hover:border-l-accent group"
            style={{ animationDelay: `${Math.min(i * 25, 200)}ms` }}
          >
            <h3 className="text-sm font-semibold text-text leading-snug group-hover:text-accent transition-colors duration-100 line-clamp-2">
              {item.title}
            </h3>

            <div className="flex items-center gap-1.5 text-[11px] text-text-muted">
              <Calendar size={12} />
              {date.toLocaleDateString("en-US", { month: "short", day: "numeric" })}
              {" · "}
              {date.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })}
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
