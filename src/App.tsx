import { useState, useCallback } from "react";
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

  const renderPage = useCallback(() => {
    switch (activeTab) {
      case "schedule":
        return <SchedulePage timezone={timezone} />;
      case "standings":
        return <StandingsPage />;
      case "news":
        return <NewsPage />;
      case "settings":
        return (
          <SettingsPage
            timezone={timezone}
            setTimezone={setTimezone}
            theme={theme}
            setTheme={setTheme}
            notifications={notifications}
          />
        );
    }
  }, [activeTab, timezone, setTimezone, theme, setTheme, notifications]);

  return (
    <div className="min-h-svh bg-bg text-text">
      <Header activeTab={activeTab} timezone={timezone} />
      <main className="pt-24 pb-20 overflow-x-hidden">
        <div key={activeTab} className="page-enter">
          {renderPage()}
        </div>
      </main>
      <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  );
}
