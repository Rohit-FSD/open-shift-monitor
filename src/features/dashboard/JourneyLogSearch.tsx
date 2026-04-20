import { useState } from "react"
import { Search, Download, FileSearch } from "lucide-react"

interface PodLogEntry {
  podName: string
  containerName: string
  serviceName: string
  matchingLines: string[]
  totalMatches: number
}

interface JourneyLogsResponse {
  searchId: string
  searchType: string
  environment: string
  namespace: string
  searchedService: string | null
  timeRangeMinutes: number
  totalPodsSearched: number
  totalMatchingLogs: number
  podLogs: PodLogEntry[]
  timestamp: string
}

interface Props {
  env: string
}

const JourneyLogSearch = ({ env }: Props) => {
  const [searchId, setSearchId] = useState("")
  const [serviceName, setServiceName] = useState("")
  const [timeRange, setTimeRange] = useState<string>("")
  const [data, setData] = useState<JourneyLogsResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSearch = async () => {
    if (!env) {
      setError("Select an environment first")
      return
    }
    if (!searchId.trim()) {
      setError("Enter a correlation ID or transaction ID")
      return
    }
    setLoading(true)
    setError(null)
    setData(null)
    try {
      const params = new URLSearchParams({ envName: env, searchId: searchId.trim() })
      if (serviceName.trim()) params.append("serviceName", serviceName.trim())
      if (timeRange) params.append("timeRangeMinutes", timeRange)

      const res = await fetch(`http://localhost:8080/api/journey-logs/search?${params}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setData(await res.json())
    } catch (e: any) {
      setError(e.message || "Search failed")
    } finally {
      setLoading(false)
    }
  }

  const handleDownload = () => {
    if (!data) return
    const lines: string[] = []
    lines.push(`# Journey Logs — ${data.searchType}: ${data.searchId}`)
    lines.push(`# Environment: ${data.environment} (${data.namespace})`)
    lines.push(`# Pods searched: ${data.totalPodsSearched} | Matches: ${data.totalMatchingLogs}`)
    lines.push(`# Timestamp: ${data.timestamp}`)
    lines.push("")
    data.podLogs.forEach(p => {
      lines.push(`===== Pod: ${p.podName} (${p.serviceName}) — ${p.totalMatches} matches =====`)
      p.matchingLines.forEach(l => lines.push(l))
      lines.push("")
    })
    const blob = new Blob([lines.join("\n")], { type: "text/plain" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `journey-logs-${data.searchId}-${Date.now()}.log`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5 mt-6">
      <div className="flex items-center gap-2 mb-4">
        <FileSearch size={18} className="text-blue-400" />
        <h3 className="text-white font-medium">Journey Log Search</h3>
        <span className="text-xs text-slate-500">Download logs by correlation ID / transaction ID</span>
      </div>

      <div className="grid grid-cols-12 gap-2 mb-3">
        <input
          value={searchId}
          onChange={e => setSearchId(e.target.value)}
          placeholder="Correlation ID or Transaction ID"
          className="col-span-5 bg-slate-800 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500"
          onKeyDown={e => e.key === "Enter" && handleSearch()}
        />
        <input
          value={serviceName}
          onChange={e => setServiceName(e.target.value)}
          placeholder="Service (optional)"
          className="col-span-3 bg-slate-800 border border-slate-700 rounded px-3 py-2 text-sm text-white placeholder-slate-500"
        />
        <select
          value={timeRange}
          onChange={e => setTimeRange(e.target.value)}
          className="col-span-2 bg-slate-800 border border-slate-700 rounded px-2 py-2 text-sm text-white"
        >
          <option value="">All available</option>
          <option value="60">Last 1 hour</option>
          <option value="360">Last 6 hours</option>
          <option value="1440">Last 24 hours</option>
        </select>
        <button
          onClick={handleSearch}
          disabled={loading || !env}
          className="col-span-2 flex items-center justify-center gap-1 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-500 text-white text-sm rounded px-3 py-2"
        >
          <Search size={14} />
          {loading ? "Searching..." : "Search"}
        </button>
      </div>

      {!env && (
        <p className="text-xs text-yellow-400">Select an environment in the control bar above to search.</p>
      )}

      {error && <p className="text-sm text-red-400">{error}</p>}

      {data && (
        <div className="mt-3">
          <div className="flex items-center justify-between mb-3 text-sm">
            <div className="text-slate-300">
              <span className="text-slate-500">Type:</span> {data.searchType} ·
              <span className="text-slate-500"> Pods:</span> {data.totalPodsSearched} ·
              <span className="text-slate-500"> Matches:</span>{" "}
              <span className={data.totalMatchingLogs > 0 ? "text-green-400" : "text-slate-500"}>
                {data.totalMatchingLogs}
              </span>
            </div>
            <button
              onClick={handleDownload}
              disabled={data.totalMatchingLogs === 0}
              className="flex items-center gap-1 bg-slate-700 hover:bg-slate-600 disabled:bg-slate-800 disabled:text-slate-600 text-white text-xs px-3 py-1.5 rounded"
            >
              <Download size={12} /> Download
            </button>
          </div>

          {data.totalMatchingLogs === 0 ? (
            <div className="text-sm text-slate-500 bg-slate-800 rounded px-3 py-2">
              No matches found. Try increasing the time range or omitting it to scan all available logs.
            </div>
          ) : (
            <div className="space-y-3 max-h-96 overflow-y-auto">
              {data.podLogs.map((p, i) => (
                <div key={i} className="bg-slate-800 rounded border border-slate-700">
                  <div className="px-3 py-2 border-b border-slate-700 flex items-center justify-between">
                    <span className="text-xs font-mono text-slate-300">{p.podName}</span>
                    <span className="text-xs text-slate-500">{p.totalMatches} matches</span>
                  </div>
                  <div className="p-2 space-y-0.5 max-h-48 overflow-y-auto">
                    {p.matchingLines.map((l, idx) => (
                      <div key={idx} className="text-xs font-mono text-slate-400 whitespace-pre-wrap break-all">
                        {l}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default JourneyLogSearch
