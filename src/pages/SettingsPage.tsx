import { TIMEZONE_OPTIONS, getTimezoneAbbr } from "../utils/time";
import { Globe, Clock, Check } from "lucide-react";

interface Props {
  timezone: string;
  setTimezone: (tz: string) => void;
  theme: string;
  setTheme: (t: string) => void;
}

export function SettingsPage({ timezone, setTimezone }: Props) {
  return (
    <div className="px-2.5 pt-2.5 space-y-2.5 max-w-2xl mx-auto">
      {/* Timezone */}
      <div className="glass rounded-xl overflow-hidden">
        <div className="px-3 py-2 border-b border-glass-border flex items-center gap-1.5">
          <Globe size={10} className="text-highlight" />
          <h3 className="font-display text-[10px] font-semibold tracking-widest text-text-muted uppercase">
            Timezone
          </h3>
        </div>

        <div className="p-1 max-h-[65vh] overflow-y-auto space-y-px">
          {TIMEZONE_OPTIONS.map((opt) => {
            const isActive = opt.value === timezone;
            const abbr = getTimezoneAbbr(opt.value);

            return (
              <button
                key={opt.value}
                onClick={() => setTimezone(opt.value)}
                className={`w-full flex items-center justify-between px-2.5 py-2 rounded-md text-left transition-all duration-100 ${
                  isActive
                    ? "bg-accent/8 border border-accent/15"
                    : "border border-transparent hover:bg-surface-light active:scale-[0.99]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <Clock size={11} className={isActive ? "text-accent" : "text-text-muted"} />
                  <div>
                    <p className={`text-[12px] font-medium leading-tight ${isActive ? "text-accent" : "text-text"}`}>
                      {opt.label}
                    </p>
                    <p className="text-[9px] text-text-muted tracking-wide">{opt.value}</p>
                  </div>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className={`text-[10px] font-mono tracking-wider ${isActive ? "text-accent" : "text-text-muted"}`}>
                    {abbr}
                  </span>
                  {isActive && <Check size={12} className="text-accent" />}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* About */}
      <div className="glass rounded-xl p-3 space-y-1.5">
        <h3 className="font-display text-[10px] font-semibold tracking-widest text-text-muted uppercase">About</h3>
        <div className="space-y-1 text-[11px] text-text-secondary leading-relaxed">
          <p>F1 Schedule Pro — 2026 Season</p>
          <p className="text-text-muted text-[10px]">All times auto-convert to your timezone.</p>
        </div>
        <div className="h-px bg-glass-border my-1.5" />
        <p className="text-[9px] text-text-muted tracking-wide">
          Schedule · Jolpica F1 API  ·  Standings · f1api.dev  ·  News · Racer.com
        </p>
      </div>
    </div>
  );
}
