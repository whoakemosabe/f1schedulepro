export interface Session {
  name: string;
  dateUtc: string; // ISO 8601 UTC
}

export interface Circuit {
  id: string;
  name: string;
  location: string;
  country: string;
  countryCode: string;
}

export interface Race {
  round: number;
  type: "conventional" | "sprint" | "testing";
  name: string;
  officialName: string;
  circuit: Circuit;
  sessions: Session[];
  f1ResultsId?: number;
  f1ResultsSlug?: string;
}

export type RaceStatus = "completed" | "active" | "upcoming";

export interface DriverStanding {
  position: number;
  points: number;
  driver: {
    name: string;
    surname: string;
    shortName?: string;
    nationality: string;
  };
  team: {
    teamId: string;
    teamName: string;
  };
}

export interface NewsItem {
  title: string;
  link: string;
  pubDate: string;
  description: string;
  source: string;
  thumbnail?: string;
}

export type TabId = "schedule" | "standings" | "news" | "settings";
