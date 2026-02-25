import type { TabId } from "../types";

const TITLES: Record<TabId, string> = {
  schedule: "Schedule",
  standings: "Standings",
  news: "News",
  settings: "Settings",
};

interface HeaderProps {
  activeTab: TabId;
}

export function Header({ activeTab }: HeaderProps) {
  return (
    <header className="glass safe-top fixed top-0 left-0 right-0 z-50 border-x-0 border-t-0 accent-bar">
      <div className="flex items-center gap-3 px-4 py-2.5">
        <div className="w-8 h-8 rounded-lg bg-accent flex items-center justify-center shadow-[0_0_16px_rgba(225,6,0,0.35)]">
          <span className="text-white font-display font-black text-[11px] leading-none">F1</span>
        </div>
        <div className="flex items-baseline gap-2.5">
          <h1 className="font-display text-[15px] font-bold tracking-widest text-text uppercase leading-none">
            {TITLES[activeTab]}
          </h1>
          <span className="text-[11px] text-text-muted tracking-[0.2em] uppercase font-medium">
            2026
          </span>
        </div>
        <div className="ml-auto flex items-center gap-1.5">
          <div className="w-1.5 h-1.5 rounded-full bg-accent opacity-80" />
          <div className="w-1.5 h-1.5 rounded-full bg-accent opacity-40" />
          <div className="w-1.5 h-1.5 rounded-full bg-accent opacity-20" />
        </div>
      </div>
    </header>
  );
}
