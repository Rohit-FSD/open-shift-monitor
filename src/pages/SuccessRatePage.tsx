import { useEffect, useState } from "react"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import useFetch from "../hooks/useFetch"

interface Filter {
  id: string
  name: string
  description: string
  category: "SUCCESS" | "FAILURE" | "ATTEMPT"
  active: boolean
  color: string
  priority: number
}

interface CategoryResult {
  filterName: string
  filterExpression: string
  category: string
  matchCount: number
  percentage: number
  color: string | null
}

interface SuccessRateResponse {
  config: any
  period: { start: string; end: string } | null
  overallSuccessRate: number
  totalAttempts: number
  successfulOperations: number
  failedOperations: number
  categoryBreakdown: CategoryResult[]
  timeSeries: any[]
  sampleLogs: any[]
  metadata: any
}

const TIME_RANGES = [
  { label: "Last 1 hour", value: 60 },
  { label: "Last 6 hours", value: 360 },
  { label: "Last 24 hours", value: 1440 },
  { label: "Last 7 days", value: 10080 },
]

const API_BASE = "http://localhost:8080/api/deployments"

const SuccessRatePage = () => {
  const [selectedEnv, setSelectedEnv] = useState("")
  const [timeRange, setTimeRange] = useState(1440)
  const [totalAttemptsFilterId, setTotalAttemptsFilterId] = useState<string>("")
  const [successIds, setSuccessIds] = useState<string[]>([])
  const [failureIds, setFailureIds] = useState<string[]>([])
  const [result, setResult] = useState<SuccessRateResponse | null>(null)
  const [calculating, setCalculating] = useState(false)
  const [calcError, setCalcError] = useState<string | null>(null)

  const { data: envData } = useFetch<Record<string, any>>("http://localhost:8080/api/openshift/environments")
  const { data: filtersRaw } = useFetch<Filter[]>(`${API_BASE}/filters?activeOnly=true`)
  const filters: Filter[] = Array.isArray(filtersRaw) ? filtersRaw : []

  useEffect(() => {
    if (envData && !selectedEnv) {
      const first = Object.keys(envData)[0]
      if (first) setSelectedEnv(first)
    }
  }, [envData])

  useEffect(() => {
    if (!filters.length) return
    if (successIds.length === 0)
      setSuccessIds(filters.filter((f: Filter) => f.category === "SUCCESS").map((f: Filter) => f.id))
    if (failureIds.length === 0)
      setFailureIds(filters.filter((f: Filter) => f.category === "FAILURE").map((f: Filter) => f.id))
    if (!totalAttemptsFilterId) {
      const attempt = filters.find((f: Filter) => f.category === "ATTEMPT")
      if (attempt) setTotalAttemptsFilterId(attempt.id)
    }
  }, [filters])

  const successFilters = filters.filter((f: Filter) => f.category === "SUCCESS")
  const failureFilters = filters.filter((f: Filter) => f.category === "FAILURE")
  const attemptFilters = filters.filter((f: Filter) => f.category === "ATTEMPT")

  const toggle = (id: string, list: string[], setList: (v: string[]) => void) =>
    setList(list.includes(id) ? list.filter((x) => x !== id) : [...list, id])

  const handleCalculate = async () => {
    if (!selectedEnv) return
    setCalculating(true)
    setCalcError(null)
    setResult(null)
    try {
      const res = await fetch(`${API_BASE}/success-rate/calculate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: `success-rate-${selectedEnv}`,
          envName: selectedEnv,
          timeRangeMinutes: timeRange,
          totalAttemptsFilterId: totalAttemptsFilterId || null,
          successFilterIds: successIds,
          failureFilterIds: failureIds,
          includeSampleLogs: true,
          groupBy: "HOUR",
        }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setResult(await res.json())
    } catch (err: any) {
      setCalcError(err.message || "Calculation failed")
    } finally {
      setCalculating(false)
    }
  }

  const rateColor = (rate: number) =>
    rate >= 95 ? "text-green-400" : rate >= 80 ? "text-yellow-400" : "text-red-400"

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-7xl mx-auto w-full">
          <div className="mb-6">
            <h1 className="text-2xl font-bold">Success Rate Calculator</h1>
            <p className="text-slate-400 text-sm mt-1">
              Analyze application login success/failure rates by parsing OpenShift logs
            </p>
          </div>

          <div className="grid grid-cols-3 gap-6">
            {/* LEFT */}
            <div className="col-span-1 space-y-4">
              <Panel label="Environment">
                <select
                  value={selectedEnv}
                  onChange={(e) => setSelectedEnv(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-sm"
                >
                  {envData &&
                    Object.keys(envData).map((env) => (
                      <option key={env} value={env}>
                        {env}
                      </option>
                    ))}
                </select>
                {selectedEnv && envData?.[selectedEnv] && (
                  <div className="mt-2 text-xs text-slate-500 space-y-0.5">
                    <div>Cluster: {envData[selectedEnv].cluster}</div>
                    <div>Namespace: {envData[selectedEnv].namespace}</div>
                    <div>Realm: {envData[selectedEnv].realm}</div>
                  </div>
                )}
              </Panel>

              <Panel label="Time Range">
                <div className="space-y-2">
                  {TIME_RANGES.map((t) => (
                    <label key={t.value} className="flex items-center gap-2 text-sm cursor-pointer">
                      <input
                        type="radio"
                        name="timeRange"
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
              </Panel>

              <Panel label="Total Attempts Filter">
                <select
                  value={totalAttemptsFilterId}
                  onChange={(e) => setTotalAttemptsFilterId(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-sm"
                >
                  <option value="">— none —</option>
                  {attemptFilters.map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
                <p className="text-xs text-slate-500 mt-2">
                  Denominator of success rate. If unset, denominator falls back to success + failure.
                </p>
              </Panel>

              <Panel label="Filters">
                {successFilters.length > 0 && (
                  <>
                    <div className="text-xs text-green-400 mb-2">SUCCESS</div>
                    {successFilters.map((f) => (
                      <FilterToggle
                        key={f.id}
                        filter={f}
                        checked={successIds.includes(f.id)}
                        onChange={() => toggle(f.id, successIds, setSuccessIds)}
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
                        checked={failureIds.includes(f.id)}
                        onChange={() => toggle(f.id, failureIds, setFailureIds)}
                      />
                    ))}
                  </>
                )}
                {!filtersRaw && <div className="text-slate-500 text-sm">Loading filters...</div>}
              </Panel>

              <button
                onClick={handleCalculate}
                disabled={calculating || !selectedEnv}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-500 text-white font-medium rounded-lg py-3 text-sm"
              >
                {calculating ? "Calculating..." : "Calculate Success Rate"}
              </button>
            </div>

            {/* RIGHT */}
            <div className="col-span-2 space-y-4">
              {!result && !calculating && !calcError && (
                <Placeholder text="Select an environment and click Calculate" />
              )}
              {calculating && <Placeholder text="Analyzing logs..." />}
              {calcError && (
                <div className="bg-red-900/20 border border-red-700 rounded-lg p-4 text-red-400">
                  {calcError}
                </div>
              )}
              {result && (
                <>
                  <div className="bg-slate-900 border border-slate-700 rounded-lg p-6">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-slate-400 text-sm mb-1">Success Rate</div>
                        <div className={`text-5xl font-bold ${rateColor(result.overallSuccessRate)}`}>
                          {result.overallSuccessRate?.toFixed(1)}%
                        </div>
                        <div className="text-slate-500 text-xs mt-2">
                          {selectedEnv} · last {timeRange} min
                        </div>
                      </div>
                      <div className="space-y-2">
                        <Stat label="Attempts" value={result.totalAttempts} color="text-white" />
                        <Stat label="Success" value={result.successfulOperations} color="text-green-400" />
                        <Stat label="Failure" value={result.failedOperations} color="text-red-400" />
                      </div>
                    </div>
                    <div className="mt-4 bg-slate-700 rounded-full h-3 overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${
                          result.overallSuccessRate >= 95
                            ? "bg-green-500"
                            : result.overallSuccessRate >= 80
                            ? "bg-yellow-500"
                            : "bg-red-500"
                        }`}
                        style={{ width: `${Math.min(result.overallSuccessRate || 0, 100)}%` }}
                      />
                    </div>
                  </div>

                  {result.categoryBreakdown?.length > 0 && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
                      <div className="text-sm font-medium mb-3">Filter Breakdown</div>
                      <div className="space-y-2">
                        {result.categoryBreakdown.map((c, i) => (
                          <div key={i} className="flex items-center justify-between text-sm">
                            <div className="flex items-center gap-2">
                              <span
                                className="w-2 h-2 rounded-full"
                                style={{ backgroundColor: c.color || "#888" }}
                              />
                              <span className="text-slate-300">{c.filterName}</span>
                              <span className="text-xs text-slate-500">({c.category})</span>
                            </div>
                            <div className="flex gap-4">
                              <span className="text-slate-400 text-xs">{c.percentage?.toFixed(1)}%</span>
                              <span className="font-medium">{c.matchCount}</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {result.sampleLogs?.length > 0 && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
                      <div className="text-sm font-medium mb-3">Sample Logs</div>
                      <div className="space-y-1 max-h-64 overflow-y-auto">
                        {result.sampleLogs.map((log, i) => (
                          <div
                            key={i}
                            className="text-xs font-mono text-slate-400 bg-slate-800 px-3 py-1.5 rounded"
                          >
                            {typeof log === "string" ? log : log.rawLine || log.message || JSON.stringify(log)}
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

const Panel = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
    <label className="text-xs text-slate-400 uppercase tracking-wider block mb-2">{label}</label>
    {children}
  </div>
)

const Placeholder = ({ text }: { text: string }) => (
  <div className="bg-slate-900 border border-slate-700 rounded-lg flex items-center justify-center h-64 text-slate-500">
    {text}
  </div>
)

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
    <input type="checkbox" checked={checked} onChange={onChange} className="w-3.5 h-3.5 accent-blue-500" />
    <span
      className="w-2 h-2 rounded-full flex-shrink-0"
      style={{ backgroundColor: filter.color || "#888" }}
    />
    <span className={checked ? "text-white" : "text-slate-500"}>{filter.name}</span>
  </label>
)

const Stat = ({ label, value, color }: { label: string; value: number; color: string }) => (
  <div className="text-right">
    <div className="text-slate-400 text-xs">{label}</div>
    <div className={`text-xl font-semibold ${color}`}>{value?.toLocaleString()}</div>
  </div>
)

export default SuccessRatePage
