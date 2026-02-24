import { useState, useEffect } from 'react'

/**
 * useDebounce — delays propagation of a rapidly-changing value.
 *
 * Used on search inputs so that the expensive patient-list filter
 * (which runs inside useMemo) only re-runs after the user has paused
 * typing for `delay` ms, rather than on every keystroke.
 *
 * @param {*}      value - The value to debounce
 * @param {number} delay - Debounce delay in milliseconds (default 300)
 * @returns {*} The debounced value
 */
export function useDebounce(value, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])
  return debounced
}
