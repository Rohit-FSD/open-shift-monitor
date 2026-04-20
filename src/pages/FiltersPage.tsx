import { useState } from "react"
import { Navigate } from "react-router-dom"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import useFetch from "../hooks/useFetch"
import { useRole } from "../context/RoleContext"

interface Condition {
  field: string
  condition: string
  value: string
  operator: string | null
}

interface Expression {
  operator: string
  conditions: Condition[]
  subExpressions: Expression[] | null
}

interface Filter {
  id: string
  name: string
  description: string
  category: "SUCCESS" | "FAILURE"
  expression: Expression
  color: string
  active: boolean
  priority: number
  createdAt: string | null
  updatedAt: string | null
}

type CategoryTab = "ALL" | "SUCCESS" | "FAILURE"

const FiltersPage = () => {
  const { isProjectManager } = useRole()
  const [activeTab, setActiveTab] = useState<CategoryTab>("ALL")
  const [activeOnly, setActiveOnly] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)

  const buildUrl = () => {
    const params = new URLSearchParams()
    if (activeOnly) params.set("activeOnly", "true")
    if (activeTab !== "ALL") params.set("category", activeTab)
    const qs = params.toString()
    return `http://localhost:8080/api/deployments/filters${qs ? `?${qs}` : ""}`
  }

  const { data: filters, loading, error } = useFetch<Filter[]>(buildUrl())

  const tabs: CategoryTab[] = ["ALL", "SUCCESS", "FAILURE"]

  if (!isProjectManager) return <Navigate to="/" replace />

  return (

    <div className="flex min-h-screen bg-slate-950 text-white">

      <Sidebar />

      <div className="flex-1 flex flex-col">

        <Navbar />

        <div className="p-6 max-w-7xl mx-auto w-full">

          {/* HEADER */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-2xl font-bold text-white">Deployment Filters</h1>
              <p className="text-slate-400 text-sm mt-1">
                Manage SUCCESS / FAILURE log filters used for success rate calculation
              </p>
            </div>
          </div>

          {/* CONTROLS */}
          <div className="flex items-center justify-between mb-4">

            {/* CATEGORY TABS */}
            <div className="flex gap-1 bg-slate-800 rounded-lg p-1">
              {tabs.map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
                    activeTab === tab
                      ? "bg-slate-600 text-white"
                      : "text-slate-400 hover:text-white"
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>

            {/* ACTIVE ONLY TOGGLE */}
            <label className="flex items-center gap-2 text-sm text-slate-400 cursor-pointer">
              <input
                type="checkbox"
                checked={activeOnly}
                onChange={(e) => setActiveOnly(e.target.checked)}
                className="w-4 h-4 accent-blue-500"
              />
              Active only
            </label>

          </div>

          {/* CONTENT */}
          {loading && (
            <div className="text-slate-400 py-12 text-center">Loading filters...</div>
          )}

          {!!error && (
            <div className="text-red-400 py-12 text-center">
              Failed to load filters. Is the backend running on port 8080?
            </div>
          )}

          {!loading && !error && (!filters || filters.length === 0) && (
            <div className="text-slate-400 py-12 text-center">No filters found.</div>
          )}

          {!loading && filters && filters.length > 0 && (
            <div className="space-y-3">
              {filters.map((filter) => (
                <FilterCard
                  key={filter.id}
                  filter={filter}
                  expanded={expanded === filter.id}
                  onToggle={() =>
                    setExpanded(expanded === filter.id ? null : filter.id)
                  }
                />
              ))}
            </div>
          )}

        </div>

      </div>

    </div>

  )
}

const FilterCard = ({
  filter,
  expanded,
  onToggle,
}: {
  filter: Filter
  expanded: boolean
  onToggle: () => void
}) => {

  const isSuccess = filter.category === "SUCCESS"
  const categoryColor = isSuccess
    ? "bg-green-500/20 text-green-400 border border-green-500/30"
    : "bg-red-500/20 text-red-400 border border-red-500/30"

  const conditions = filter.expression?.conditions || []

  return (

    <div className="bg-slate-900 border border-slate-700 rounded-lg overflow-hidden">

      {/* CARD HEADER */}
      <div
        className="flex items-center justify-between px-5 py-4 cursor-pointer hover:bg-slate-800/50 transition-colors"
        onClick={onToggle}
      >

        <div className="flex items-center gap-4">

          {/* COLOR DOT */}
          <div
            className="w-3 h-3 rounded-full flex-shrink-0"
            style={{ backgroundColor: filter.color || "#888" }}
          />

          {/* NAME + DESCRIPTION */}
          <div>
            <div className="flex items-center gap-3">
              <span className="font-medium text-white">{filter.name}</span>
              <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${categoryColor}`}>
                {filter.category}
              </span>
              {!filter.active && (
                <span className="text-xs px-2 py-0.5 rounded-full bg-slate-700 text-slate-400">
                  Inactive
                </span>
              )}
            </div>
            <div className="text-slate-400 text-sm mt-0.5">{filter.description}</div>
          </div>

        </div>

        <div className="flex items-center gap-6 text-sm text-slate-400">
          <span>Priority: <span className="text-white">{filter.priority}</span></span>
          <span className="text-slate-600">{expanded ? "▲" : "▼"}</span>
        </div>

      </div>

      {/* EXPANDED CONDITIONS */}
      {expanded && (
        <div className="border-t border-slate-700 px-5 py-4 bg-slate-800/30">

          <div className="text-xs text-slate-400 uppercase tracking-wider mb-3">
            Expression — operator: <span className="text-white font-medium">{filter.expression?.operator}</span>
          </div>

          {conditions.length > 0 ? (
            <div className="space-y-2">
              {conditions.map((cond, idx) => (
                <div
                  key={idx}
                  className="flex items-center gap-3 bg-slate-800 rounded px-4 py-2 text-sm"
                >
                  <span className="text-slate-400">field:</span>
                  <span className="text-blue-300">{cond.field}</span>
                  <span className="text-slate-500">|</span>
                  <span className="text-slate-400">condition:</span>
                  <span className="text-yellow-300">{cond.condition}</span>
                  <span className="text-slate-500">|</span>
                  <span className="text-slate-400">value:</span>
                  <span className="text-green-300 font-mono">{cond.value}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-slate-500 text-sm">No direct conditions (see subExpressions)</div>
          )}

          {/* SUB EXPRESSIONS */}
          {filter.expression?.subExpressions && filter.expression.subExpressions.length > 0 && (
            <div className="mt-4">
              <div className="text-xs text-slate-400 uppercase tracking-wider mb-2">
                Sub-expressions
              </div>
              {filter.expression.subExpressions.map((sub, si) => (
                <div key={si} className="ml-4 border-l-2 border-slate-600 pl-4 mb-3">
                  <div className="text-xs text-slate-500 mb-2">
                    operator: <span className="text-white">{sub.operator}</span>
                  </div>
                  {sub.conditions?.map((cond, ci) => (
                    <div
                      key={ci}
                      className="flex items-center gap-3 bg-slate-800 rounded px-4 py-2 text-sm mb-1"
                    >
                      <span className="text-slate-400">field:</span>
                      <span className="text-blue-300">{cond.field}</span>
                      <span className="text-slate-500">|</span>
                      <span className="text-slate-400">condition:</span>
                      <span className="text-yellow-300">{cond.condition}</span>
                      <span className="text-slate-500">|</span>
                      <span className="text-slate-400">value:</span>
                      <span className="text-green-300 font-mono">{cond.value}</span>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}

          <div className="mt-3 text-xs text-slate-500">
            ID: {filter.id}
          </div>

        </div>
      )}

    </div>

  )
}

export default FiltersPage
