import { useRef, useEffect, useState } from "react";

interface Props {
  value: string;
  className?: string;
}

export function FlipDigit({ value, className = "" }: Props) {
  const [display, setDisplay] = useState(value);
  const [flipping, setFlipping] = useState(false);
  const prevRef = useRef(value);

  useEffect(() => {
    if (value !== prevRef.current) {
      setFlipping(true);
      const t = setTimeout(() => {
        setDisplay(value);
        setFlipping(false);
        prevRef.current = value;
      }, 150);
      return () => clearTimeout(t);
    }
  }, [value]);

  return (
    <span className={`flip-digit ${className}`}>
      <span className={flipping ? "flip-old" : "flip-current"}>{display}</span>
      {flipping && <span className="flip-new">{value}</span>}
    </span>
  );
}
