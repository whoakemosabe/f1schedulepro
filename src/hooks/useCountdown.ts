import { useState, useEffect } from "react";
import { getCountdown, type Countdown } from "../utils/time";

export function useCountdown(targetUtc: string | null): Countdown | null {
  const [countdown, setCountdown] = useState<Countdown | null>(
    targetUtc ? getCountdown(targetUtc) : null
  );

  useEffect(() => {
    if (!targetUtc) {
      setCountdown(null);
      return;
    }
    setCountdown(getCountdown(targetUtc));
    const id = setInterval(() => setCountdown(getCountdown(targetUtc)), 1000);
    return () => clearInterval(id);
  }, [targetUtc]);

  return countdown;
}
