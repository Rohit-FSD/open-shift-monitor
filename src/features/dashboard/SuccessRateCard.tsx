import { useEffect, useState } from "react"
import useFetch from "../../hooks/useFetch"
import { TrendingUp, RefreshCw } from "lucide-react"

interface Filter {
  id: string
  name: string
  category: "SUCCESS" | "FAILURE" | "ATTEMPT"
  active: boolean
  color: string
}

interface SuccessRateResponse {
  overallSuccessRate: number
  totalAttempts: number
  successfulOperations: number
  failedOperations: number
}

interface Props {
  env: string
}

const TIME_OPTIONS = [
  { label: "1h", value: 60 },
  { label: "6h", value: 360 },
  { label: "24h", value: 1440 },
]

const SuccessRateCard = ({ env }: Props) => {
  const [timeRange, setTimeRange] = useState(1440)
  const [result, setResult] = useState<SuccessRateResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: filters } = useFetch<Filter[]>(
    "http://localhost:8080/api/deployments/filters?activeOnly=true"
  )

  const calculate = async () => {
    if (!env || !filters) return
    setLoading(true)
    setError(null)
    try {
      const successIds = filters.filter(f => f.category === "SUCCESS").map(f => f.id)
      const failureIds = filters.filter(f => f.category === "FAILURE").map(f => f.id)
      const attempt = filters.find(f => f.category === "ATTEMPT")

      const res = await fetch("http://localhost:8080/api/deployments/success-rate/calculate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: `dashboard-${env}`,
          envName: env,
          timeRangeMinutes: timeRange,
          totalAttemptsFilterId: attempt?.id || null,
          successFilterIds: successIds,
          failureFilterIds: failureIds,
          includeSampleLogs: false,
          groupBy: "HOUR",
        }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setResult(await res.json())
    } catch (e: any) {
      setError(e.message || "Failed to calculate success rate")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (env && filters) calculate()
  }, [env, filters, timeRange])

  const rate = result?.overallSuccessRate ?? 0
  const rateColor =
    rate >= 95 ? "text-green-400" : rate >= 80 ? "text-yellow-400" : "text-red-400"
  const barColor =
    rate >= 95 ? "bg-green-500" : rate >= 80 ? "bg-yellow-500" : "bg-red-500"

  return (
    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <TrendingUp size={16} className="text-blue-400" />
          <h3 className="text-white font-medium text-sm">Success Rate</h3>
        </div>
        <div className="flex items-center gap-1">
          {TIME_OPTIONS.map(t => (
            <button
              key={t.value}
              onClick={() => setTimeRange(t.value)}
              className={`text-xs px-2 py-1 rounded ${
                timeRange === t.value
                  ? "bg-blue-600 text-white"
                  : "bg-slate-800 text-slate-400 hover:text-white"
              }`}
            >
              {t.label}
            </button>
          ))}
          <button
            onClick={calculate}
            disabled={loading || !env}
            className="ml-1 p-1 text-slate-400 hover:text-white disabled:opacity-50"
            title="Recalculate"
          >
            <RefreshCw size={12} className={loading ? "animate-spin" : ""} />
          </button>
        </div>
      </div>

      {!env && (
        <div className="text-xs text-slate-500 py-6 text-center">
          Select an environment to see success rate
        </div>
      )}

      {env && error && (
        <div className="text-xs text-red-400 bg-red-900/20 rounded px-2 py-1.5">{error}</div>
      )}

      {env && !error && (
        <>
          <div className={`text-4xl font-bold ${rateColor}`}>
            {loading && !result ? "—" : `${rate.toFixed(1)}%`}
          </div>
          <div className="mt-3 bg-slate-700 rounded-full h-2 overflow-hidden">
            <div
              className={`h-full transition-all ${barColor}`}
              style={{ width: `${Math.min(rate, 100)}%` }}
            />
          </div>

          <div className="grid grid-cols-3 gap-2 mt-4">
            <Stat label="Attempts" value={result?.totalAttempts} color="text-white" />
            <Stat label="Success" value={result?.successfulOperations} color="text-green-400" />
            <Stat label="Failure" value={result?.failedOperations} color="text-red-400" />
          </div>
        </>
      )}
    </div>
  )
}

const Stat = ({ label, value, color }: { label: string; value?: number; color: string }) => (
  <div>
    <div className="text-[10px] text-slate-500 uppercase">{label}</div>
    <div className={`text-sm font-semibold ${color}`}>{value?.toLocaleString() ?? "—"}</div>
  </div>
)

export default SuccessRateCard
