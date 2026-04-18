import { useState, useEffect } from "react"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import useFetch from "../hooks/useFetch"

interface Filter {
  id: string
  name: string
  category: "SUCCESS" | "FAILURE"
  description: string
  active: boolean
  color: string
}

interface SuccessRateResult {
  environment: string
  timeRangeMinutes: number
  totalCount: number
  successCount: number
  failureCount: number
  successRate: number
  filterBreakdown: Record<string, number>
  sampleLogs: string[]
  calculatedAt: string
}

const TIME_RANGES = [
  { label: "Last 1 hour", value: 60 },
  { label: "Last 6 hours", value: 360 },
  { label: "Last 24 hours", value: 1440 },
  { label: "Last 7 days", value: 10080 },
]

const SuccessRatePage = () => {

  const [selectedEnv, setSelectedEnv] = useState("")
  const [timeRange, setTimeRange] = useState(1440)
  const [selectedFilterIds, setSelectedFilterIds] = useState<string[]>([])
  const [result, setResult] = useState<SuccessRateResult | null>(null)
  const [calculating, setCalculating] = useState(false)
  const [calcError, setCalcError] = useState<string | null>(null)

  // Load environments
  const { data: envData } = useFetch("http://localhost:8080/api/openshift/environments")

  // Load filters (active only)
  const { data: filters } = useFetch<Filter[]>(
    "http://localhost:8080/api/deployments/filters?activeOnly=true"
  )

  // Auto-select first env
  useEffect(() => {
    if (envData && !selectedEnv) {
      const first = Object.keys(envData)[0]
      if (first) setSelectedEnv(first)
    }
  }, [envData])

  // Auto-select all active filters
  useEffect(() => {
    if (filters && filters.length > 0 && selectedFilterIds.length === 0) {
      setSelectedFilterIds(filters.map((f) => f.id))
    }
  }, [filters])

  const successFilters = (filters || []).filter((f) => f.category === "SUCCESS")
  const failureFilters = (filters || []).filter((f) => f.category === "FAILURE")

  const toggleFilter = (id: string) => {
    setSelectedFilterIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    )
  }

  const handleCalculate = async () => {
    if (!selectedEnv) return
    setCalculating(true)
    setCalcError(null)
    setResult(null)

    try {
      const res = await fetch("http://localhost:8080/api/deployments/success-rate/calculate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          environment: selectedEnv,
          timeRangeMinutes: timeRange,
          filterIds: selectedFilterIds,
        }),
      })

      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const json = await res.json()
      setResult(json)
    } catch (err: any) {
      setCalcError(err.message || "Calculation failed")
    } finally {
      setCalculating(false)
    }
  }

  const rateColor = (rate: number) => {
    if (rate >= 95) return "text-green-400"
    if (rate >= 80) return "text-yellow-400"
    return "text-red-400"
  }

  return (

    <div className="flex min-h-screen bg-slate-950 text-white">

      <Sidebar />

      <div className="flex-1 flex flex-col">

        <Navbar />

        <div className="p-6 max-w-7xl mx-auto w-full">

          {/* HEADER */}
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-white">Success Rate Calculator</h1>
            <p className="text-slate-400 text-sm mt-1">
              Analyze application login success/failure rates by parsing OpenShift logs
            </p>
          </div>

          <div className="grid grid-cols-3 gap-6">

            {/* LEFT PANEL — CONTROLS */}
            <div className="col-span-1 space-y-4">

              {/* ENVIRONMENT */}
              <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
                <label className="text-xs text-slate-400 uppercase tracking-wider block mb-2">
                  Environment
                </label>
                <select
                  value={selectedEnv}
                  onChange={(e) => setSelectedEnv(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-sm text-white"
                >
                  {envData &&
                    Object.keys(envData).map((env) => (
                      <option key={env} value={env}>{env}</option>
                    ))}
                </select>
                {selectedEnv && envData?.[selectedEnv] && (
                  <div className="mt-2 text-xs text-slate-500 space-y-0.5">
                    <div>Cluster: {envData[selectedEnv].cluster}</div>
                    <div>Namespace: {envData[selectedEnv].namespace}</div>
                    <div>Realm: {envData[selectedEnv].realm}</div>
                  </div>
                )}
              </div>

              {/* TIME RANGE */}
              <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
                <label className="text-xs text-slate-400 uppercase tracking-wider block mb-2">
                  Time Range
                </label>
                <div className="space-y-2">
                  {TIME_RANGES.map((t) => (
                    <label
                      key={t.value}
                      className="flex items-center gap-2 text-sm cursor-pointer"
                    >
                      <input
                        type="radio"
                        name="timeRange"
                        value={t.value}
                        checked={timeRange === t.value}
                        onChange={() => setTimeRange(t.value)}
                        className="accent-blue-500"
                      />
                      <span className={timeRange === t.value ? "text-white" : "text-slate-400"}>
                        {t.label}
                      </span>
                    </label>
                  ))}
                </div>
              </div>

              {/* FILTER SELECTION */}
              <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
                <label className="text-xs text-slate-400 uppercase tracking-wider block mb-3">
                  Filters
                </label>

                {successFilters.length > 0 && (
                  <>
                    <div className="text-xs text-green-400 mb-2">SUCCESS</div>
                    {successFilters.map((f) => (
                      <FilterToggle
                        key={f.id}
                        filter={f}
                        checked={selectedFilterIds.includes(f.id)}
                        onChange={() => toggleFilter(f.id)}
                      />
                    ))}
                  </>
                )}

                {failureFilters.length > 0 && (
                  <>
                    <div className="text-xs text-red-400 mt-3 mb-2">FAILURE</div>
                    {failureFilters.map((f) => (
                      <FilterToggle
                        key={f.id}
                        filter={f}
                        checked={selectedFilterIds.includes(f.id)}
                        onChange={() => toggleFilter(f.id)}
                      />
                    ))}
                  </>
                )}

                {!filters && (
                  <div className="text-slate-500 text-sm">Loading filters...</div>
                )}
              </div>

              {/* CALCULATE BUTTON */}
              <button
                onClick={handleCalculate}
                disabled={calculating || !selectedEnv}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-500 text-white font-medium rounded-lg py-3 text-sm transition-colors"
              >
                {calculating ? "Calculating..." : "Calculate Success Rate"}
              </button>

            </div>

            {/* RIGHT PANEL — RESULTS */}
            <div className="col-span-2 space-y-4">

              {!result && !calculating && !calcError && (
                <div className="bg-slate-900 border border-slate-700 rounded-lg flex items-center justify-center h-64 text-slate-500">
                  Select an environment and click Calculate
                </div>
              )}

              {calculating && (
                <div className="bg-slate-900 border border-slate-700 rounded-lg flex items-center justify-center h-64 text-slate-400">
                  Analyzing logs...
                </div>
              )}

              {calcError && (
                <div className="bg-red-900/20 border border-red-700 rounded-lg p-4 text-red-400">
                  {calcError}
                </div>
              )}

              {result && (
                <>

                  {/* MAIN RATE */}
                  <div className="bg-slate-900 border border-slate-700 rounded-lg p-6">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-slate-400 text-sm mb-1">Success Rate</div>
                        <div className={`text-5xl font-bold ${rateColor(result.successRate)}`}>
                          {result.successRate?.toFixed(1)}%
                        </div>
                        <div className="text-slate-500 text-xs mt-2">
                          {result.environment} · last {result.timeRangeMinutes} min
                        </div>
                      </div>
                      <div className="text-right space-y-2">
                        <Stat label="Total" value={result.totalCount} color="text-white" />
                        <Stat label="Success" value={result.successCount} color="text-green-400" />
                        <Stat label="Failure" value={result.failureCount} color="text-red-400" />
                      </div>
                    </div>

                    {/* PROGRESS BAR */}
                    <div className="mt-4 bg-slate-700 rounded-full h-3 overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          result.successRate >= 95 ? "bg-green-500" :
                          result.successRate >= 80 ? "bg-yellow-500" : "bg-red-500"
                        }`}
                        style={{ width: `${Math.min(result.successRate, 100)}%` }}
                      />
                    </div>
                  </div>

                  {/* FILTER BREAKDOWN */}
                  {result.filterBreakdown && Object.keys(result.filterBreakdown).length > 0 && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
                      <div className="text-sm font-medium text-white mb-3">Filter Breakdown</div>
                      <div className="space-y-2">
                        {Object.entries(result.filterBreakdown).map(([name, count]) => (
                          <div key={name} className="flex items-center justify-between text-sm">
                            <span className="text-slate-300">{name}</span>
                            <span className="text-white font-medium">{count}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* SAMPLE LOGS */}
                  {result.sampleLogs && result.sampleLogs.length > 0 && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
                      <div className="text-sm font-medium text-white mb-3">Sample Logs</div>
                      <div className="space-y-1 max-h-64 overflow-y-auto">
                        {result.sampleLogs.map((log, i) => (
                          <div
                            key={i}
                            className="text-xs font-mono text-slate-400 bg-slate-800 px-3 py-1.5 rounded"
                          >
                            {log}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                </>
              )}

            </div>

          </div>

        </div>

      </div>

    </div>

  )
}

const FilterToggle = ({
  filter,
  checked,
  onChange,
}: {
  filter: Filter
  checked: boolean
  onChange: () => void
}) => (
  <label className="flex items-center gap-2 text-sm cursor-pointer mb-1.5">
    <input
      type="checkbox"
      checked={checked}
      onChange={onChange}
      className="w-3.5 h-3.5 accent-blue-500"
    />
    <span
      className="w-2 h-2 rounded-full flex-shrink-0"
      style={{ backgroundColor: filter.color || "#888" }}
    />
    <span className={checked ? "text-white" : "text-slate-500"}>{filter.name}</span>
  </label>
)

const Stat = ({
  label,
  value,
  color,
}: {
  label: string
  value: number
  color: string
}) => (
  <div className="text-right">
    <div className="text-slate-400 text-xs">{label}</div>
    <div className={`text-xl font-semibold ${color}`}>{value?.toLocaleString()}</div>
  </div>
)

export default SuccessRatePage
