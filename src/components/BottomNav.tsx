import { useRef, useEffect, useState } from "react";
import {
  Calendar,
  Trophy,
  Newspaper,
  Settings,
  type LucideIcon,
} from "lucide-react";
import type { TabId } from "../types";

interface Tab {
  id: TabId;
  label: string;
  icon: LucideIcon;
}

const TABS: Tab[] = [
  { id: "schedule", label: "Schedule", icon: Calendar },
  { id: "standings", label: "Standings", icon: Trophy },
  { id: "news", label: "News", icon: Newspaper },
  { id: "settings", label: "Settings", icon: Settings },
];

interface BottomNavProps {
  activeTab: TabId;
  onTabChange: (tab: TabId) => void;
}

export function BottomNav({ activeTab, onTabChange }: BottomNavProps) {
  const navRef = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!navRef.current) return;
    const btn = navRef.current.querySelector(
      `[data-tab="${activeTab}"]`
    ) as HTMLElement | null;
    if (!btn) return;

    const navRect = navRef.current.getBoundingClientRect();
    const btnRect = btn.getBoundingClientRect();
    setIndicator({
      left: btnRect.left - navRect.left + btnRect.width * 0.15,
      width: btnRect.width * 0.7,
    });
    if (!ready) setReady(true);
  }, [activeTab, ready]);

  return (
    <nav className="glass safe-bottom fixed bottom-0 left-0 right-0 z-50 border-x-0 border-b-0">
      {/* Top accent line */}
      <div className="h-px bg-linear-to-r from-transparent via-accent/50 to-transparent" />
      <div ref={navRef} className="relative flex items-stretch max-w-lg mx-auto">
        {/* Sliding bouncy indicator */}
        <div
          className={`tab-indicator absolute top-0 h-0.75 rounded-full bg-accent shadow-[0_0_12px_rgba(225,6,0,0.6)] ${ready ? "" : "opacity-0"}`}
          style={{ left: indicator.left, width: indicator.width }}
        />

        {TABS.map((tab) => {
          const isActive = tab.id === activeTab;
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              data-tab={tab.id}
              onClick={() => onTabChange(tab.id)}
              className="flex-1 flex flex-col items-center gap-0.5 py-2.5 pt-3 relative min-h-13"
            >
              <Icon
                size={22}
                strokeWidth={isActive ? 2.5 : 1.5}
                className={`transition-all duration-200 ${
                  isActive
                    ? "text-accent drop-shadow-[0_0_10px_rgba(225,6,0,0.6)] scale-110"
                    : "text-text-muted"
                }`}
              />
              <span
                className={`text-[11px] tracking-wider font-semibold uppercase transition-all duration-200 ${
                  isActive ? "text-accent" : "text-text-muted/60"
                }`}
              >
                {tab.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
