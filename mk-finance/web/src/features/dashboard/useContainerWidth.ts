import { useEffect, useRef, useState } from "react";

/**
 * Measures a container's width with ResizeObserver. Recharts charts are only
 * mounted once this is > 0, which does two things at once: it keeps the charts
 * off the initial render until we know the size, and it means jsdom (where
 * layout is 0 and ResizeObserver is absent) never mounts them — so the render
 * tests assert on the accessible DOM, not on SVG that can't lay out there.
 */
export function useContainerWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const element = ref.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) setWidth(entry.contentRect.width);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return { ref, width };
}
