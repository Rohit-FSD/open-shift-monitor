import { useEffect, useState } from "react"

const API_BASE = "http://localhost:8080/api/log-failure-detection"

/**
 * Fetches log failure detection for a service in an environment.
 * Only runs when both environment and serviceName are provided (e.g. when a service is selected).
 */
const useLogFailures = (environment?: string, serviceName?: string) => {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(Boolean(environment && serviceName))

  const fetchFailures = async () => {
    if (!environment || !serviceName) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const res = await fetch(
        `${API_BASE}/${encodeURIComponent(environment)}/${encodeURIComponent(serviceName)}`
      )
      const json = await res.json()
      setData(json)
    } catch (e) {
      console.error("Failed to load log failures", e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchFailures()
  }, [environment, serviceName])

  return { data, loading }
}

export default useLogFailures