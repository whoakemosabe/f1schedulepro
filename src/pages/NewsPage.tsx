import { useState, useRef, useCallback, useEffect } from "react";
import { createPortal } from "react-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw, ExternalLink, AlertCircle, Clock, Check, Tv, Youtube, Bookmark, Newspaper, MessageCircle, Calendar, ArrowUpDown } from "lucide-react";
import { fetchNews, SOURCE_COLORS } from "../api/fetchers";

const QUICK_LINKS = [
  { label: "F1 TV", url: "https://f1tv.formula1.com", icon: Tv, color: "text-highlight" },
  { label: "YouTube", url: "https://www.youtube.com/F1", icon: Youtube, color: "text-live" },
  { label: "Motorsport", url: "https://www.motorsport.com/f1/", icon: Newspaper, color: "text-accent" },
  { label: "Reddit", url: "https://www.reddit.com/r/formula1/", icon: MessageCircle, color: "text-warning" },
  { label: "Calendar", url: "https://f1calendar.com", icon: Calendar, color: "text-success" },
  { label: "FIA Rules", url: "https://www.fia.com/regulation/category/110", icon: Bookmark, color: "text-text-muted" },
];

type SortMode = "newest" | "oldest" | "source";
const SORT_LABELS: Record<SortMode, string> = {
  newest: "Newest",
  oldest: "Oldest",
  source: "Source",
};

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

function stripHtml(html: string): string {
  const tmp = document.createElement("div");
  tmp.innerHTML = html;
  return tmp.textContent ?? tmp.innerText ?? "";
}

interface Props {
  isActive: boolean;
}

export function NewsPage({ isActive }: Props) {
  const hasBeenActive = useRef(false);
  if (isActive) hasBeenActive.current = true;

  // Scroll to top whenever the tab is re-opened
  useEffect(() => {
    if (isActive) window.scrollTo({ top: 0, behavior: "instant" });
  }, [isActive]);

  const queryClient = useQueryClient();
  const { data: news = [], isLoading, error, isFetching } = useQuery({
    queryKey: ["news"],
    queryFn: fetchNews,
    staleTime: 5 * 60 * 1000,
    enabled: hasBeenActive.current,
  });

  const [sortMode, setSortMode] = useState<SortMode>("newest");
  const [sortOpen, setSortOpen] = useState(false);
  const sortRef = useRef<HTMLDivElement>(null);

  const [toastVisible, setToastVisible] = useState(false);
  const [toastType, setToastType] = useState<"success" | "error">("success");
  const toastTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const manualRefresh = useRef(false);
  const wasFetching = useRef(false);

  const showToast = useCallback((type: "success" | "error") => {
    clearTimeout(toastTimer.current);
    setToastType(type);
    setToastVisible(true);
    toastTimer.current = setTimeout(() => setToastVisible(false), 2000);
  }, []);

  if (wasFetching.current && !isFetching && manualRefresh.current) {
    manualRefresh.current = false;
    showToast(error ? "error" : "success");
  }
  wasFetching.current = isFetching;

  const handleRefresh = () => {
    manualRefresh.current = true;
    queryClient.invalidateQueries({ queryKey: ["news"] });
  };

  const refreshing = isFetching && !isLoading;

  const sortedNews = [...news].sort((a, b) => {
    if (sortMode === "newest") return new Date(b.pubDate).getTime() - new Date(a.pubDate).getTime();
    if (sortMode === "oldest") return new Date(a.pubDate).getTime() - new Date(b.pubDate).getTime();
    return a.source.localeCompare(b.source);
  });

  return (
    <div className="px-3 pt-3 pb-2 space-y-2.5 max-w-2xl mx-auto">
      {/* Toast */}
      {createPortal(
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
            {toastType === "success" ? <Check size={14} strokeWidth={3} /> : <AlertCircle size={14} />}
            <span className="text-xs font-bold tracking-wide">
              {toastType === "success" ? "News updated" : "Refresh failed"}
            </span>
          </div>
        </div>,
        document.body
      )}

      {/* Quick Links — compact pill row */}
      <div className="flex flex-wrap gap-1.5">
        {QUICK_LINKS.map((link) => {
          const Icon = link.icon;
          return (
            <a
              key={link.label}
              href={link.url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-surface-light border border-glass-border text-[12px] font-medium text-text-secondary active:border-accent/40 active:text-text transition-colors duration-100"
            >
              <Icon size={12} className={`${link.color} shrink-0`} />
              {link.label}
            </a>
          );
        })}
      </div>

      {/* Section header with sort + refresh */}
      <div className="flex items-center justify-between px-1">
        <div className="flex items-center gap-2">
          <h2 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase">
            Latest News
          </h2>
          {!isLoading && !error && news.length > 0 && (
            <span className="text-[10px] text-text-muted/60 tabular-nums">{news.length} articles</span>
          )}
        </div>

        <div className="flex items-center gap-1.5">
          {/* Sort dropdown */}
          <div ref={sortRef} className="relative">
            <button
              onClick={() => setSortOpen((o) => !o)}
              className="inline-flex items-center gap-1 px-2 py-1 rounded-lg bg-surface-light border border-glass-border text-[11px] font-semibold text-text-secondary active:border-accent/40 transition-colors duration-100"
            >
              <ArrowUpDown size={11} className="text-text-muted" />
              {SORT_LABELS[sortMode]}
            </button>
            {sortOpen && (
              <div className="absolute right-0 top-full mt-1 w-28 rounded-xl bg-surface border border-border-light shadow-xl z-50 overflow-hidden">
                {(["newest", "oldest", "source"] as SortMode[]).map((mode) => (
                  <button
                    key={mode}
                    onClick={() => { setSortMode(mode); setSortOpen(false); }}
                    className={`w-full px-3 py-2 text-left text-[12px] font-medium transition-colors duration-100 ${
                      sortMode === mode
                        ? "bg-accent/10 text-accent"
                        : "text-text active:bg-surface-light"
                    }`}
                  >
                    {SORT_LABELS[mode]}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Refresh */}
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className={`p-1.5 rounded-lg transition-all duration-150 disabled:opacity-40 ${
              refreshing ? "text-accent bg-accent/10" : "text-text-muted active:text-accent active:bg-surface-light"
            }`}
          >
            <RefreshCw size={14} className={`transition-transform duration-300 ${refreshing ? "animate-spin" : ""}`} />
          </button>
        </div>
      </div>

      {/* Loading skeletons */}
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="skeleton h-24 rounded-xl" />
          ))}
        </div>
      )}

      {/* Error */}
      {error && !isLoading && (
        <div className="glass rounded-xl p-6 text-center space-y-3">
          <AlertCircle size={28} className="mx-auto text-live" />
          <p className="text-sm text-text-secondary">{error instanceof Error ? error.message : "Failed to load news"}</p>
          <button onClick={handleRefresh} className="inline-flex items-center gap-1.5 text-xs font-semibold text-accent">
            <RefreshCw size={12} /> Retry
          </button>
        </div>
      )}

      {/* Articles */}
      <div className={`space-y-2 transition-opacity duration-200 ${refreshing ? "opacity-40" : "opacity-100"}`}>
        {!isLoading && !error && sortedNews.map((item, i) => {
          const date = new Date(item.pubDate);
          const excerpt = stripHtml(item.description).slice(0, 140);
          const isFeatured = i === 0 && sortMode === "newest";
          const sourceColor = SOURCE_COLORS[item.source] ?? "text-text-muted";

          return (
            <a
              key={`${item.source}-${item.title}`}
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
