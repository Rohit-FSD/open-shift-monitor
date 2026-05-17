import { useEffect, useState, useRef } from "react"
import { Navigate } from "react-router-dom"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import useFetch from "../hooks/useFetch"
import { useRole } from "../context/RoleContext"
import {
  PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer,
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  BarChart, Bar,
} from "recharts"

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

interface TimeSeriesPoint {
  timestamp: string
  totalAttempts: number
  successCount: number
  failureCount: number
  successRate: number
}

interface SuccessRateResponse {
  config: any
  period: { start: string; end: string } | null
  overallSuccessRate: number
  totalAttempts: number
  successfulOperations: number
  failedOperations: number
  categoryBreakdown: CategoryResult[]
  timeSeries: TimeSeriesPoint[]
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

// ─── colour helpers ────────────────────────────────────────────────────────────
const categoryColor = (cat: string, customColor: string | null) => {
  if (customColor) return customColor
  if (cat === "SUCCESS") return "#22c55e"
  if (cat === "FAILURE") return "#ef4444"
  return "#64748b"
}

const rateColor = (rate: number) =>
  rate >= 95 ? "#22c55e" : rate >= 80 ? "#eab308" : "#ef4444"

const rateTextClass = (rate: number) =>
  rate >= 95 ? "text-green-400" : rate >= 80 ? "text-yellow-400" : "text-red-400"

// ─── custom donut label ────────────────────────────────────────────────────────
const DonutLabel = ({ cx, cy, rate }: { cx: number; cy: number; rate: number }) => (
  <>
    <text x={cx} y={cy - 10} textAnchor="middle" dominantBaseline="central"
      fill={rateColor(rate)} fontSize={28} fontWeight="bold">
      {rate.toFixed(1)}%
    </text>
    <text x={cx} y={cy + 18} textAnchor="middle" fill="#94a3b8" fontSize={12}>
      Success Rate
    </text>
  </>
)

// ─── page ──────────────────────────────────────────────────────────────────────
const SuccessRatePage = () => {
  const { isProjectManager } = useRole()
  const [selectedEnv, setSelectedEnv] = useState("")
  const [timeRange, setTimeRange] = useState(1440)
  const [totalAttemptsFilterId, setTotalAttemptsFilterId] = useState<string>("")
  const [successIds, setSuccessIds] = useState<string[]>([])
  const [failureIds, setFailureIds] = useState<string[]>([])
  const [result, setResult] = useState<SuccessRateResponse | null>(null)
  const [calculating, setCalculating] = useState(false)
  const [calcError, setCalcError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<"overview" | "breakdown" | "timeline" | "logs">("overview")
  const [showCalcInfo, setShowCalcInfo] = useState(false)

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
      setSuccessIds(filters.filter(f => f.category === "SUCCESS").map(f => f.id))
    if (failureIds.length === 0)
      setFailureIds(filters.filter(f => f.category === "FAILURE").map(f => f.id))
    if (!totalAttemptsFilterId) {
      const attempt = filters.find(f => f.category === "ATTEMPT")
      if (attempt) setTotalAttemptsFilterId(attempt.id)
    }
  }, [filters])

  const successFilters = filters.filter(f => f.category === "SUCCESS")
  const failureFilters = filters.filter(f => f.category === "FAILURE")
  const attemptFilters = filters.filter(f => f.category === "ATTEMPT")

  const toggle = (id: string, list: string[], setList: (v: string[]) => void) =>
    setList(list.includes(id) ? list.filter(x => x !== id) : [...list, id])

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
          groupBy: timeRange <= 60 ? "MINUTE" : timeRange <= 1440 ? "HOUR" : "DAY",
        }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setResult(await res.json())
      setActiveTab("overview")
    } catch (err: any) {
      setCalcError(err.message || "Calculation failed")
    } finally {
      setCalculating(false)
    }
  }

  if (!isProjectManager) return <Navigate to="/" replace />

  // ── derived chart data ────────────────────────────────────────────────────
  const donutData = result ? [
    { name: "Success", value: result.successfulOperations, fill: "#22c55e" },
    { name: "Failure", value: result.failedOperations, fill: "#ef4444" },
    {
      name: "Other",
      value: Math.max(0, result.totalAttempts - result.successfulOperations - result.failedOperations),
      fill: "#475569",
    },
  ].filter(d => d.value > 0) : []

  const breakdownData = (result?.categoryBreakdown ?? []).map(c => ({
    name: c.filterName.length > 20 ? c.filterName.slice(0, 18) + "…" : c.filterName,
    fullName: c.filterName,
    count: c.matchCount,
    pct: Number(c.percentage?.toFixed(1) ?? 0),
    fill: categoryColor(c.category, c.color),
    category: c.category,
  }))

  const timelineData = (result?.timeSeries ?? []).map(p => ({
    time: (() => {
      try {
        const d = new Date(p.timestamp)
        return timeRange <= 1440
          ? d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
          : d.toLocaleDateString([], { month: "2-digit", day: "2-digit" })
      } catch { return p.timestamp }
    })(),
    successRate: Number(p.successRate?.toFixed(2) ?? 0),
    success: p.successCount,
    failure: p.failureCount,
    total: p.totalAttempts,
  }))

  const rate = result?.overallSuccessRate ?? 0

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-7xl mx-auto w-full">

          <div className="mb-6 flex items-start justify-between">
            <div>
              <h1 className="text-2xl font-bold">Success Rate Calculator</h1>
              <p className="text-slate-400 text-sm mt-1">
                Analyze application login success/failure rates by parsing OpenShift logs
              </p>
            </div>
            <button
              onClick={() => setShowCalcInfo(true)}
              title="How is this calculated?"
              className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 px-3 py-1.5 rounded-lg transition-colors mt-1"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24"
                fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
              How it's calculated
            </button>
          </div>

          {showCalcInfo && <CalcInfoModal onClose={() => setShowCalcInfo(false)} />}

          <div className="grid grid-cols-3 gap-6">

            {/* ── LEFT CONFIG PANEL ─────────────────────────────────────── */}
            <div className="col-span-1 space-y-4">

              <Panel label="Environment">
                <select
                  value={selectedEnv}
                  onChange={e => setSelectedEnv(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-sm"
                >
                  {envData && Object.keys(envData).map(env => (
                    <option key={env} value={env}>{env}</option>
                  ))}
                </select>
                {selectedEnv && envData && envData[selectedEnv] && (
                  <div className="mt-2 text-xs text-slate-500 space-y-0.5">
                    <div>Cluster: {(envData[selectedEnv] as any).cluster}</div>
                    <div>Namespace: {(envData[selectedEnv] as any).namespace}</div>
                    <div>Realm: {(envData[selectedEnv] as any).realm}</div>
                  </div>
                )}
              </Panel>

              <Panel label="Time Range">
                <div className="space-y-2">
                  {TIME_RANGES.map(t => (
                    <label key={t.value} className="flex items-center gap-2 text-sm cursor-pointer">
                      <input
                        type="radio" name="timeRange"
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
                  onChange={e => setTotalAttemptsFilterId(e.target.value)}
                  className="w-full bg-slate-800 border border-slate-600 rounded px-3 py-2 text-sm"
                >
                  <option value="">— none —</option>
                  {attemptFilters.map(f => (
                    <option key={f.id} value={f.id}>{f.name}</option>
                  ))}
                </select>
                <p className="text-xs text-slate-500 mt-2">
                  Denominator for success rate. Falls back to success + failure if unset.
                </p>
              </Panel>

              <Panel label="Filters">
                {successFilters.length > 0 && (
                  <>
                    <div className="text-xs text-green-400 mb-2">SUCCESS</div>
                    {successFilters.map(f => (
                      <FilterToggle key={f.id} filter={f}
                        checked={successIds.includes(f.id)}
                        onChange={() => toggle(f.id, successIds, setSuccessIds)} />
                    ))}
                  </>
                )}
                {failureFilters.length > 0 && (
                  <>
                    <div className="text-xs text-red-400 mt-3 mb-2">FAILURE</div>
                    {failureFilters.map(f => (
                      <FilterToggle key={f.id} filter={f}
                        checked={failureIds.includes(f.id)}
                        onChange={() => toggle(f.id, failureIds, setFailureIds)} />
                    ))}
                  </>
                )}
                {!filtersRaw && <div className="text-slate-500 text-sm">Loading filters…</div>}
              </Panel>

              <button
                onClick={handleCalculate}
                disabled={calculating || !selectedEnv}
                className="w-full bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-500 text-white font-medium rounded-lg py-3 text-sm transition-colors"
              >
                {calculating ? "Calculating…" : "Calculate Success Rate"}
              </button>
            </div>

            {/* ── RIGHT RESULTS PANEL ───────────────────────────────────── */}
            <div className="col-span-2 space-y-4">

              {!result && !calculating && !calcError && (
                <Placeholder text="Select an environment and click Calculate" />
              )}
              {calculating && <Placeholder text="Analyzing logs…" />}
              {calcError && (
                <div className="bg-red-900/20 border border-red-700 rounded-lg p-4 text-red-400">
                  {calcError}
                </div>
              )}

              {result && (
                <>
                  {/* ── KPI row ─────────────────────────────────────────── */}
                  <div className="grid grid-cols-3 gap-3">
                    <KpiCard label="Total Attempts" value={result.totalAttempts.toLocaleString()} color="text-white" />
                    <KpiCard label="Successful" value={result.successfulOperations.toLocaleString()} color="text-green-400" />
                    <KpiCard label="Failed" value={result.failedOperations.toLocaleString()} color="text-red-400" />
                  </div>

                  {/* ── tabs ────────────────────────────────────────────── */}
                  <div className="flex gap-1 bg-slate-900 border border-slate-700 rounded-lg p-1">
                    {(["overview", "breakdown", "timeline", "logs"] as const).map(tab => (
                      <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        className={`flex-1 py-1.5 text-xs font-medium rounded capitalize transition-colors ${
                          activeTab === tab
                            ? "bg-blue-600 text-white"
                            : "text-slate-400 hover:text-white"
                        }`}
                      >
                        {tab}
                      </button>
                    ))}
                  </div>

                  {/* ── OVERVIEW tab ────────────────────────────────────── */}
                  {activeTab === "overview" && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
                      <div className="text-sm font-medium mb-4 text-slate-300">Success vs Failure</div>
                      <div className="flex items-center gap-6">

                        {/* Donut */}
                        <div className="w-56 h-56 flex-shrink-0">
                          <ResponsiveContainer width="100%" height="100%">
                            <PieChart>
                              <Pie
                                data={donutData}
                                cx="50%" cy="50%"
                                innerRadius={68} outerRadius={95}
                                startAngle={90} endAngle={-270}
                                paddingAngle={2}
                                dataKey="value"
                                labelLine={false}
                              >
                                {donutData.map((d, i) => (
                                  <Cell key={i} fill={d.fill} stroke="transparent" />
                                ))}
                              </Pie>
                              <DonutLabel cx={112} cy={112} rate={rate} />
                              <Tooltip
                                contentStyle={{ background: "#1e293b", border: "1px solid #334155", borderRadius: 8 }}
                                itemStyle={{ color: "#cbd5e1" }}
                                formatter={(v: number) => [v.toLocaleString(), ""]}
                              />
                            </PieChart>
                          </ResponsiveContainer>
                        </div>

                        {/* Right-side stats */}
                        <div className="flex-1 space-y-4">
                          {donutData.map(d => (
                            <div key={d.name}>
                              <div className="flex justify-between text-xs text-slate-400 mb-1">
                                <span className="flex items-center gap-2">
                                  <span className="w-2 h-2 rounded-full inline-block" style={{ background: d.fill }} />
                                  {d.name}
                                </span>
                                <span style={{ color: d.fill }} className="font-semibold">
                                  {result.totalAttempts
                                    ? ((d.value / result.totalAttempts) * 100).toFixed(1)
                                    : "0"}%
                                </span>
                              </div>
                              <div className="bg-slate-700 rounded-full h-2 overflow-hidden">
                                <div
                                  className="h-full rounded-full transition-all"
                                  style={{
                                    width: `${result.totalAttempts ? Math.min((d.value / result.totalAttempts) * 100, 100) : 0}%`,
                                    background: d.fill,
                                  }}
                                />
                              </div>
                              <div className="text-right text-xs text-slate-500 mt-0.5">
                                {d.value.toLocaleString()} events
                              </div>
                            </div>
                          ))}

                          <div className={`text-3xl font-bold mt-2 ${rateTextClass(rate)}`}>
                            {rate.toFixed(2)}%
                            <span className="text-sm font-normal text-slate-400 ml-2">overall success</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* ── BREAKDOWN tab ───────────────────────────────────── */}
                  {activeTab === "breakdown" && breakdownData.length > 0 && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5 space-y-5">
                      <div className="text-sm font-medium text-slate-300">Filter Breakdown</div>

                      {/* Horizontal bar chart */}
                      <div style={{ height: Math.max(180, breakdownData.length * 44) }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart
                            data={breakdownData}
                            layout="vertical"
                            margin={{ top: 0, right: 40, left: 10, bottom: 0 }}
                          >
                            <CartesianGrid strokeDasharray="3 3" stroke="#334155" horizontal={false} />
                            <XAxis type="number" tick={{ fill: "#94a3b8", fontSize: 11 }} tickLine={false} axisLine={false} />
                            <YAxis
                              type="category" dataKey="name" width={130}
                              tick={{ fill: "#94a3b8", fontSize: 11 }} tickLine={false} axisLine={false}
                            />
                            <Tooltip
                              contentStyle={{ background: "#1e293b", border: "1px solid #334155", borderRadius: 8 }}
                              itemStyle={{ color: "#cbd5e1" }}
                              formatter={(v: number, _: string, p: any) => [
                                `${v.toLocaleString()} (${p.payload.pct}%)`, p.payload.fullName,
                              ]}
                            />
                            <Bar dataKey="count" radius={[0, 4, 4, 0]} maxBarSize={28}>
                              {breakdownData.map((d, i) => (
                                <Cell key={i} fill={d.fill} />
                              ))}
                            </Bar>
                          </BarChart>
                        </ResponsiveContainer>
                      </div>

                      {/* Detail table */}
                      <div className="border border-slate-700 rounded-lg overflow-hidden">
                        <table className="w-full text-xs">
                          <thead>
                            <tr className="bg-slate-800 text-slate-400">
                              <th className="text-left px-3 py-2">Filter</th>
                              <th className="text-center px-3 py-2">Category</th>
                              <th className="text-right px-3 py-2">Count</th>
                              <th className="text-right px-3 py-2">Share</th>
                            </tr>
                          </thead>
                          <tbody>
                            {result.categoryBreakdown.map((c, i) => (
                              <tr key={i} className="border-t border-slate-800 hover:bg-slate-800/50">
                                <td className="px-3 py-2 text-slate-300 flex items-center gap-2">
                                  <span className="w-2 h-2 rounded-full flex-shrink-0"
                                    style={{ background: categoryColor(c.category, c.color) }} />
                                  {c.filterName}
                                </td>
                                <td className="px-3 py-2 text-center">
                                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${
                                    c.category === "SUCCESS" ? "bg-green-900/40 text-green-400" :
                                    c.category === "FAILURE" ? "bg-red-900/40 text-red-400" :
                                    "bg-slate-700 text-slate-400"
                                  }`}>
                                    {c.category}
                                  </span>
                                </td>
                                <td className="px-3 py-2 text-right font-mono text-white">
                                  {c.matchCount.toLocaleString()}
                                </td>
                                <td className="px-3 py-2 text-right text-slate-400">
                                  {c.percentage?.toFixed(1)}%
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                  {activeTab === "breakdown" && breakdownData.length === 0 && (
                    <Placeholder text="No category breakdown data available" />
                  )}

                  {/* ── TIMELINE tab ────────────────────────────────────── */}
                  {activeTab === "timeline" && timelineData.length > 0 && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5 space-y-5">
                      <div className="text-sm font-medium text-slate-300">Success Rate Over Time</div>

                      {/* Area chart — success rate % */}
                      <div style={{ height: 200 }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={timelineData} margin={{ top: 5, right: 10, left: 0, bottom: 0 }}>
                            <defs>
                              <linearGradient id="successGrad" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#22c55e" stopOpacity={0.3} />
                                <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                            <XAxis dataKey="time" tick={{ fill: "#94a3b8", fontSize: 10 }} tickLine={false} axisLine={false} />
                            <YAxis domain={[0, 100]} tickFormatter={v => `${v}%`}
                              tick={{ fill: "#94a3b8", fontSize: 10 }} tickLine={false} axisLine={false} />
                            <Tooltip
                              contentStyle={{ background: "#1e293b", border: "1px solid #334155", borderRadius: 8 }}
                              itemStyle={{ color: "#cbd5e1" }}
                              formatter={(v: number) => [`${v.toFixed(2)}%`, "Success Rate"]}
                            />
                            <Area
                              type="monotone" dataKey="successRate"
                              stroke="#22c55e" strokeWidth={2}
                              fill="url(#successGrad)"
                              dot={false} activeDot={{ r: 4 }}
                            />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>

                      {/* Stacked bar — success vs failure counts */}
                      <div className="text-xs text-slate-400 mb-1">Success vs Failure Counts</div>
                      <div style={{ height: 160 }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={timelineData} margin={{ top: 0, right: 10, left: 0, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                            <XAxis dataKey="time" tick={{ fill: "#94a3b8", fontSize: 10 }} tickLine={false} axisLine={false} />
                            <YAxis tick={{ fill: "#94a3b8", fontSize: 10 }} tickLine={false} axisLine={false} />
                            <Tooltip
                              contentStyle={{ background: "#1e293b", border: "1px solid #334155", borderRadius: 8 }}
                              itemStyle={{ color: "#cbd5e1" }}
                            />
                            <Legend wrapperStyle={{ fontSize: 11, color: "#94a3b8" }} />
                            <Bar dataKey="success" name="Success" stackId="a" fill="#22c55e" radius={[0, 0, 0, 0]} />
                            <Bar dataKey="failure" name="Failure" stackId="a" fill="#ef4444" radius={[3, 3, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}

                  {activeTab === "timeline" && timelineData.length === 0 && (
                    <Placeholder text="No time-series data — try a wider time range or groupBy granularity" />
                  )}

                  {/* ── LOGS tab ─────────────────────────────────────────── */}
                  {activeTab === "logs" && (
                    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
                      <div className="text-sm font-medium mb-3 text-slate-300">
                        Sample Logs
                        {result.sampleLogs?.length > 0 && (
                          <span className="ml-2 text-xs text-slate-500">({result.sampleLogs.length})</span>
                        )}
                      </div>
                      {result.sampleLogs?.length > 0 ? (
                        <div className="space-y-1 max-h-96 overflow-y-auto">
                          {result.sampleLogs.map((log, i) => (
                            <div key={i}
                              className="text-xs font-mono text-slate-400 bg-slate-800 px-3 py-1.5 rounded">
                              {typeof log === "string" ? log : log.rawLine || log.message || JSON.stringify(log)}
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="text-slate-500 text-sm">No sample logs returned.</div>
                      )}
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

// ─── small components ──────────────────────────────────────────────────────────
const Panel = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div className="bg-slate-900 border border-slate-700 rounded-lg p-4">
    <label className="text-xs text-slate-400 uppercase tracking-wider block mb-2">{label}</label>
    {children}
  </div>
)

const Placeholder = ({ text }: { text: string }) => (
  <div className="bg-slate-900 border border-slate-700 rounded-lg flex items-center justify-center h-48 text-slate-500 text-sm">
    {text}
  </div>
)

const FilterToggle = ({
  filter, checked, onChange,
}: {
  filter: Filter; checked: boolean; onChange: () => void
}) => (
  <label className="flex items-center gap-2 text-sm cursor-pointer mb-1.5">
    <input type="checkbox" checked={checked} onChange={onChange} className="w-3.5 h-3.5 accent-blue-500" />
    <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: filter.color || "#888" }} />
    <span className={checked ? "text-white" : "text-slate-500"}>{filter.name}</span>
  </label>
)

const KpiCard = ({ label, value, color }: { label: string; value: string; color: string }) => (
  <div className="bg-slate-900 border border-slate-700 rounded-lg px-4 py-3">
    <div className="text-xs text-slate-400 mb-1">{label}</div>
    <div className={`text-2xl font-bold ${color}`}>{value}</div>
  </div>
)

const CalcInfoModal = ({ onClose }: { onClose: () => void }) => {
  const ref = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    document.addEventListener("mousedown", handler)
    return () => document.removeEventListener("mousedown", handler)
  }, [onClose])

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div ref={ref}
        className="bg-slate-900 border border-slate-700 rounded-xl shadow-2xl w-full max-w-lg p-6 space-y-5">

        {/* header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-white font-semibold">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
              fill="none" stroke="#60a5fa" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            How Success Rate is Calculated
          </div>
          <button onClick={onClose}
            className="text-slate-500 hover:text-white text-lg leading-none">✕</button>
        </div>

        {/* formula */}
        <div className="bg-slate-800 rounded-lg px-5 py-4 text-center space-y-2">
          <div className="text-slate-400 text-xs uppercase tracking-widest mb-3">Formula</div>
          <div className="text-white text-base font-mono">
            Success Rate =
          </div>
          <div className="flex items-center justify-center gap-2 text-sm font-mono">
            <div className="text-center">
              <div className="text-green-400 font-semibold">Matching SUCCESS log lines</div>
              <div className="border-t border-slate-500 mt-1 pt-1 text-slate-300">
                Total ATTEMPT log lines
              </div>
            </div>
            <div className="text-slate-400 text-lg ml-2">× 100</div>
          </div>
          <div className="text-xs text-slate-500 pt-1">
            If no ATTEMPT filter is set → denominator = SUCCESS + FAILURE counts
          </div>
        </div>

        {/* steps */}
        <div className="space-y-3">
          <div className="text-xs text-slate-400 uppercase tracking-widest">Step by Step</div>
          {[
            {
              step: "1", color: "bg-blue-600",
              title: "Fetch logs from OpenShift",
              desc: "Raw pod logs are pulled for the selected environment and time window via the Kubernetes API.",
            },
            {
              step: "2", color: "bg-purple-600",
              title: "Parse log lines",
              desc: "Each line is parsed into a structured entry (timestamp, level, service, message).",
            },
            {
              step: "3", color: "bg-yellow-600",
              title: "Apply filters",
              desc: "Your selected SUCCESS and FAILURE filters run regex/keyword matches against each log line.",
            },
            {
              step: "4", color: "bg-green-600",
              title: "Calculate the rate",
              desc: "SUCCESS matches ÷ ATTEMPT matches × 100. Results are also grouped by time bucket for the timeline chart.",
            },
          ].map(({ step, color, title, desc }) => (
            <div key={step} className="flex gap-3">
              <div className={`${color} text-white text-xs font-bold w-6 h-6 rounded-full flex-shrink-0 flex items-center justify-center mt-0.5`}>
                {step}
              </div>
              <div>
                <div className="text-sm text-white font-medium">{title}</div>
                <div className="text-xs text-slate-400 mt-0.5">{desc}</div>
              </div>
            </div>
          ))}
        </div>

        {/* thresholds */}
        <div className="border-t border-slate-700 pt-4">
          <div className="text-xs text-slate-400 uppercase tracking-widest mb-2">Health Thresholds</div>
          <div className="flex gap-3 text-xs">
            <span className="flex items-center gap-1.5 text-green-400">
              <span className="w-2 h-2 rounded-full bg-green-500 inline-block" /> ≥ 95% Healthy
            </span>
            <span className="flex items-center gap-1.5 text-yellow-400">
              <span className="w-2 h-2 rounded-full bg-yellow-500 inline-block" /> 80–94% Warning
            </span>
            <span className="flex items-center gap-1.5 text-red-400">
              <span className="w-2 h-2 rounded-full bg-red-500 inline-block" /> &lt; 80% Critical
            </span>
          </div>
        </div>

        <button onClick={onClose}
          className="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm rounded-lg py-2 transition-colors">
          Got it
        </button>
      </div>
    </div>
  )
}

export default SuccessRatePage
