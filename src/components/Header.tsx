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
    <header className="glass safe-top sticky top-0 z-50 accent-bar">
      <div className="flex items-center gap-2.5 px-3 py-2">
        <div className="w-7 h-7 rounded-md bg-accent flex items-center justify-center shadow-[0_0_12px_rgba(225,6,0,0.3)]">
          <span className="text-white font-display font-black text-[10px] leading-none">F1</span>
        </div>
        <div className="flex items-baseline gap-2">
          <h1 className="font-display text-[13px] font-bold tracking-widest text-text uppercase leading-none">
            {TITLES[activeTab]}
          </h1>
          <span className="text-[9px] text-text-muted tracking-[0.2em] uppercase font-medium">
            2026
          </span>
        </div>
        <div className="ml-auto flex items-center gap-1">
          <div className="w-1 h-1 rounded-full bg-accent opacity-80" />
          <div className="w-1 h-1 rounded-full bg-accent opacity-40" />
          <div className="w-1 h-1 rounded-full bg-accent opacity-20" />
        </div>
      </div>
    </header>
  );
}
