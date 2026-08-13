import { useCallback, useEffect, useState } from "react";

type UseDataOptions = {
  /** When set, silently reloads on this interval (does not flash the loading state). */
  refreshIntervalMs?: number;
};

export function useData<T>(
  loader: () => Promise<T>,
  dependencies: unknown[] = [],
  options: UseDataOptions = {},
) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);

  const reload = useCallback((quiet = false) => {
    if (!quiet) {
      setLoading(true);
      setError("");
    }
    setVersion((value) => value + 1);
  }, []);

  useEffect(() => {
    let active = true;
    loader()
      .then((value) => active && setData(value))
      .catch(
        (reason: unknown) =>
          active &&
          setError(
            reason instanceof Error
              ? reason.message
              : "Data could not be loaded.",
          ),
      )
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
    // Callers provide stable repository functions.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencies, version]);

  useEffect(() => {
    if (!options.refreshIntervalMs || options.refreshIntervalMs <= 0) return;
    const timer = window.setInterval(() => {
      reload(true);
    }, options.refreshIntervalMs);
    return () => window.clearInterval(timer);
  }, [options.refreshIntervalMs, reload]);

  return { data, loading, error, reload: () => reload(false) };
}
