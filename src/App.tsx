import { useState, useCallback } from "react";
import { BottomNav } from "./components/BottomNav";
import { Header } from "./components/Header";
import { SchedulePage } from "./pages/SchedulePage";
import { StandingsPage } from "./pages/StandingsPage";
import { NewsPage } from "./pages/NewsPage";
import { SettingsPage } from "./pages/SettingsPage";
import { useSettings } from "./hooks/useSettings";
import type { TabId } from "./types";

export default function App() {
  const [activeTab, setActiveTab] = useState<TabId>("schedule");
  const { timezone, setTimezone, theme, setTheme } = useSettings();

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
          />
        );
    }
  }, [activeTab, timezone, setTimezone, theme, setTheme]);

  return (
    <div className="flex flex-col min-h-svh bg-bg text-text">
      <Header activeTab={activeTab} />
      <main className="flex-1 pb-16 overflow-y-auto">
        <div key={activeTab} className="page-enter">
          {renderPage()}
        </div>
      </main>
      <BottomNav activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  );
}
