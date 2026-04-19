import { useState, useCallback } from "react"

export interface LogFailureRequest {
  envName: string
  serviceName: string
  timeRangeMinutes?: number
  includeSamples?: boolean
  sampleLimit?: number
}

export interface ApplicationError {
  errorCode: string
  errorMessage: string
  endpoint?: string
  occurrences: number
  severity: string
  recommendedSolution: string
  description: string
  steps: string[]
  sampleLogs: string[]
}

export interface PodFailure {
  podName: string
  reason: string
  message?: string
  restartCount: number
  occurrences: number
  recommendedSolution: string
  sampleLogs: string[]
}

export interface DownstreamFailure {
  dependency: string
  protocol: string
  sampleError: string
  occurrences: number
  recommendedSolution: string
  sampleLogs: string[]
}

export interface FailureSummary {
  totalLogsScanned: number
  totalFailures: number
  applicationErrorCount: number
  podFailureCount: number
  downstreamFailureCount: number
}

export interface LogFailureResponse {
  envName: string
  serviceName: string
  healthStatus: "HEALTHY" | "DEGRADED" | "DOWN"
  analyzedAt: string
  timeRangeMinutes: number
  summary: FailureSummary
  applicationErrors: ApplicationError[]
  podFailures: PodFailure[]
  downstreamFailures: DownstreamFailure[]
}

const useLogFailures = () => {
  const [data, setData] = useState<LogFailureResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const analyze = useCallback(async (req: LogFailureRequest) => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch("http://localhost:8080/api/log-failure-detection/analyze", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(req),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setData(await res.json())
    } catch (e: any) {
      setError(e.message ?? "Failed to fetch failure data")
    } finally {
      setLoading(false)
    }
  }, [])

  const reset = () => { setData(null); setError(null) }

  return { data, loading, error, analyze, reset }
}

export default useLogFailures
