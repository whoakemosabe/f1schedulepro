import { useState } from "react";
import { BottomNav } from "./components/BottomNav";
import { Header } from "./components/Header";
import { SchedulePage } from "./pages/SchedulePage";
import { StandingsPage } from "./pages/StandingsPage";
import { NewsPage } from "./pages/NewsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { useSettings } from "./hooks/useSettings";
import { useNotifications } from "./hooks/useNotifications";
import type { TabId } from "./types";

export default function App() {
  const [activeTab, setActiveTab] = useState<TabId>("schedule");
  const { timezone, setTimezone, theme, setTheme } = useSettings();
  const notifications = useNotifications();

  return (
    <div className="min-h-svh bg-bg text-text">
      <Header activeTab={activeTab} timezone={timezone} />
      <main className="pt-24 pb-20 overflow-x-hidden">
        <div className={activeTab === "schedule" ? "page-enter" : "hidden"}>
          <SchedulePage timezone={timezone} />
        </div>
        <div className={activeTab === "standings" ? "page-enter" : "hidden"}>
          <StandingsPage />
        </div>
        <div className={activeTab === "news" ? "page-enter" : "hidden"}>
          <NewsPage />
        </div>
        <div className={activeTab === "settings" ? "page-enter" : "hidden"}>
          <SettingsPage
            timezone={timezone}
            setTimezone={setTimezone}
            theme={theme}
            setTheme={setTheme}
            notifications={notifications}
          />
        </div>
      </main>
      <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  );
}
