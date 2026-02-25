import { useState, useRef, useEffect } from "react";
import { TIMEZONE_OPTIONS, getTimezoneAbbr } from "../utils/time";
import { Globe, ChevronDown, Check, Info } from "lucide-react";

interface Props {
  timezone: string;
  setTimezone: (tz: string) => void;
  theme: string;
  setTheme: (t: string) => void;
}

export function SettingsPage({ timezone, setTimezone }: Props) {
  const [open, setOpen] = useState(false);
  const dropRef = useRef<HTMLDivElement>(null);

  const current = TIMEZONE_OPTIONS.find((o) => o.value === timezone);
  const abbr = getTimezoneAbbr(timezone);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (dropRef.current && !dropRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  return (
    <div className="px-3 pt-3 pb-2 space-y-2.5 max-w-2xl mx-auto">
      {/* Timezone card */}
      <div className="glass-light rounded-xl overflow-visible border-l-[3px] border-l-highlight/30 relative z-10">
        <div className="px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Globe size={14} className="text-highlight" />
            <h2 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase">
              Timezone
            </h2>
          </div>

          {/* Dropdown */}
          <div ref={dropRef} className="relative">
            <button
              onClick={() => setOpen(!open)}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-light border border-border hover:border-border-light transition-colors duration-150"
            >
              <span className="text-sm font-semibold text-text">{current?.label ?? timezone}</span>
              <span className="text-[11px] text-accent font-mono">{abbr}</span>
              <ChevronDown
                size={14}
                className={`text-text-muted transition-transform duration-200 ${open ? "rotate-180" : ""}`}
              />
            </button>

            {open && (
              <div className="absolute right-0 top-full mt-1.5 w-64 max-h-72 overflow-y-auto rounded-xl bg-surface border border-border-light shadow-xl z-50">
                {TIMEZONE_OPTIONS.map((opt) => {
                  const isActive = opt.value === timezone;
                  const optAbbr = getTimezoneAbbr(opt.value);

                  return (
                    <button
                      key={opt.value}
                      onClick={() => { setTimezone(opt.value); setOpen(false); }}
                      className={`w-full flex items-center justify-between px-3.5 py-2.5 text-left transition-colors duration-100 ${
                        isActive
                          ? "bg-accent/10 text-accent"
                          : "text-text hover:bg-surface-light"
                      }`}
                    >
                      <div>
                        <p className={`text-sm font-medium ${isActive ? "text-accent" : "text-text"}`}>
                          {opt.label}
                        </p>
                        <p className="text-[11px] text-text-muted">{opt.value}</p>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <span className={`text-[11px] font-mono ${isActive ? "text-accent" : "text-text-secondary"}`}>
                          {optAbbr}
                        </span>
                        {isActive && <Check size={14} className="text-accent" />}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* About card */}
      <div className="glass-light rounded-xl overflow-hidden border-l-[3px] border-l-highlight/30">
        <div className="px-4 py-2.5 flex items-center gap-2 border-b border-glass-border">
          <Info size={14} className="text-highlight" />
          <h2 className="font-display text-xs font-semibold tracking-widest text-text-muted uppercase">
            About
          </h2>
        </div>

        <div className="p-4 space-y-3">
          <div className="space-y-1.5">
            <p className="text-sm font-semibold text-text">F1 Schedule Pro — 2026 Season</p>
            <p className="text-xs text-text-secondary leading-relaxed">
              All session times auto-convert to your selected timezone. Data refreshes automatically.
            </p>
          </div>

          <div className="h-px bg-border/50" />

          <div className="space-y-1 text-[11px] text-text-muted tracking-wide leading-relaxed">
            <p>Schedule · Jolpica F1 API</p>
            <p>Standings · f1api.dev</p>
            <p>News · Racer.com</p>
          </div>

          <div className="h-px bg-border/50" />

          {/* Korben credit */}
          <div className="text-center pt-1">
            <span className="korben-tag">
              Made with <span className="korben-heart">♥</span> by Korben // {new Date().getFullYear()}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
