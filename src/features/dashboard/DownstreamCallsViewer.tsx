import { useState } from "react"
import {
  Search,
  Download,
  Network,
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Clock,
  PlugZap,
  GitBranch,
  List,
} from "lucide-react"

type CallStatus = "SUCCESS" | "CLIENT_ERROR" | "SERVER_ERROR" | "TIMEOUT" | "CONN_ERROR"

interface DownstreamApiCall {
  id: string
  serviceName: string
  podName: string
  correlationId: string | null
  requestTimestamp: string | null
  method: string
  url: string | null
  requestBody: string | null
  responseBody: string | null
  callStatus: CallStatus
}

interface DownstreamCallsResponse {
  searchId: string
  environment: string
  namespace: string
  totalPodsSearched: number
  totalCalls: number
  successCount: number
  errorCount: number
  calls: DownstreamApiCall[]
  timestamp: string
}

interface Props {
  env: string
}

const STATUS_META: Record<CallStatus, { label: string; cls: string; dot: string; Icon: typeof CheckCircle2 }> = {
  SUCCESS:      { label: "Success",      cls: "text-green-400  bg-green-500/10  border-green-500/30",  dot: "bg-green-400",  Icon: CheckCircle2 },
  CLIENT_ERROR: { label: "Client error", cls: "text-amber-400  bg-amber-500/10  border-amber-500/30",  dot: "bg-amber-400",  Icon: AlertTriangle },
  SERVER_ERROR: { label: "Server error", cls: "text-red-400    bg-red-500/10    border-red-500/30",    dot: "bg-red-400",    Icon: XCircle },
  TIMEOUT:      { label: "Timeout",      cls: "text-orange-400 bg-orange-500/10 border-orange-500/30", dot: "bg-orange-400", Icon: Clock },
  CONN_ERROR:   { label: "Conn error",   cls: "text-red-400    bg-red-500/10    border-red-500/30",    dot: "bg-red-400",    Icon: PlugZap },
}

const METHOD_CLS: Record<string, string> = {
  SOAP:   "bg-purple-500/15 text-purple-300 border-purple-500/30",
  GET:    "bg-sky-500/15    text-sky-300    border-sky-500/30",
  POST:   "bg-blue-500/15   text-blue-300   border-blue-500/30",
  PUT:    "bg-indigo-500/15 text-indigo-300 border-indigo-500/30",
  DELETE: "bg-rose-500/15   text-rose-300   border-rose-500/30",
  PATCH:  "bg-teal-500/15   text-teal-300   border-teal-500/30",
}

const prettyBody = (body: string | null): string => {
  if (!body) return ""
  const trimmed = body.trim()
  if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
    try { return JSON.stringify(JSON.parse(trimmed), null, 2) } catch { /* fall through */ }
  }
  return body
}

const StatusBadge = ({ status }: { status: CallStatus }) => {
  const meta = STATUS_META[status] ?? STATUS_META.SUCCESS
  const { Icon } = meta
  return (
    <span className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border ${meta.cls}`}>
      <Icon size={11} /> {meta.label}
    </span>
  )
}

const MethodBadge = ({ method }: { method: string }) => {
  const cls = METHOD_CLS[method.toUpperCase()] ?? "bg-slate-500/15 text-slate-300 border-slate-500/30"
  return (
    <span className={`text-[10px] font-mono font-medium px-1.5 py-0.5 rounded border ${cls}`}>
      {method}
    </span>
  )
}

// ─── List view ────────────────────────────────────────────────────────────────
const CallRow = ({ call, index }: { call: DownstreamApiCall; index: number }) => {
  const [open, setOpen] = useState(false)
  const ts = call.requestTimestamp ? call.requestTimestamp.replace("T", " ").slice(0, 23) : "—"

  return (
    <div className="bg-slate-800 rounded border border-slate-700">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-slate-700/40"
      >
        <span className="text-xs text-slate-500 font-mono w-6 text-right">{index + 1}</span>
        {open ? <ChevronDown size={14} className="text-slate-400" /> : <ChevronRight size={14} className="text-slate-400" />}
        <MethodBadge method={call.method} />
        <span className="text-xs font-mono text-slate-300 truncate flex-1">{call.url ?? "—"}</span>
        <span className="text-[10px] font-mono text-slate-500 hidden md:inline">{ts}</span>
        <StatusBadge status={call.callStatus} />
      </button>

      {open && (
        <div className="px-3 pb-3 pt-1 border-t border-slate-700 grid grid-cols-1 md:grid-cols-2 gap-3">
          <BodyPanel title="Request"  body={call.requestBody}  />
          <BodyPanel title="Response" body={call.responseBody} />
          <div className="md:col-span-2 text-[11px] text-slate-500 font-mono space-x-3">
            <span><span className="text-slate-600">id:</span> {call.id}</span>
            <span><span className="text-slate-600">pod:</span> {call.podName}</span>
            <span><span className="text-slate-600">service:</span> {call.serviceName}</span>
            {call.correlationId && (
              <span><span className="text-slate-600">corr:</span> {call.correlationId}</span>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

const BodyPanel = ({ title, body }: { title: string; body: string | null }) => {
  const [copied, setCopied] = useState(false)
  const text = prettyBody(body)
  const copy = async () => {
    if (!text) return
    await navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 1200)
  }
  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-[11px] uppercase tracking-wide text-slate-500">{title}</span>
        {text && (
          <button onClick={copy} className="text-[10px] text-slate-500 hover:text-slate-300">
            {copied ? "copied" : "copy"}
          </button>
        )}
      </div>
      {text ? (
        <pre className="text-[11px] font-mono text-slate-300 bg-slate-900 border border-slate-700 rounded p-2 max-h-72 overflow-auto whitespace-pre-wrap break-all">
{text}
        </pre>
      ) : (
        <div className="text-[11px] text-slate-600 italic bg-slate-900 border border-slate-700 rounded p-2">
          (empty)
        </div>
      )}
    </div>
  )
}

// ─── Flow / tree view ─────────────────────────────────────────────────────────
const FlowNode = ({ call, index, isLast }: { call: DownstreamApiCall; index: number; isLast: boolean }) => {
  const [open, setOpen] = useState(false)
  const meta = STATUS_META[call.callStatus] ?? STATUS_META.SUCCESS
  const { Icon } = meta
  const ts = call.requestTimestamp ? call.requestTimestamp.replace("T", " ").slice(11, 19) : null

  // extract a short label from the URL
  const urlLabel = (() => {
    if (!call.url) return call.serviceName || "Unknown"
    try {
      const u = new URL(call.url)
      // last two path segments
      const parts = u.pathname.split("/").filter(Boolean)
      return parts.slice(-2).join("/") || u.hostname
    } catch {
      // not a full URL — take last segment of whatever string
      const parts = call.url.split(/[/?]/).filter(Boolean)
      return parts[parts.length - 1] || call.url
    }
  })()

  return (
    <div className="flex">
      {/* vertical spine */}
      <div className="flex flex-col items-center mr-3 flex-shrink-0">
        <div className={`w-3 h-3 rounded-full border-2 mt-1 flex-shrink-0 ${
          call.callStatus === "SUCCESS" ? "border-green-400 bg-green-400/20" : "border-red-400 bg-red-400/20"
        }`} />
        {!isLast && <div className="w-px flex-1 bg-slate-700 mt-1" style={{ minHeight: 32 }} />}
      </div>

      {/* card */}
      <div className="flex-1 mb-3">
        <button
          onClick={() => setOpen(o => !o)}
          className={`w-full text-left rounded-lg border px-3 py-2 transition-colors hover:bg-slate-800/80 ${
            call.callStatus === "SUCCESS"
              ? "border-slate-700 bg-slate-800"
              : "border-red-900/50 bg-red-950/20"
          }`}
        >
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs font-mono text-slate-500 w-5">{index + 1}</span>
            <MethodBadge method={call.method} />
            <span className="text-xs text-slate-200 font-medium truncate max-w-[180px]" title={call.url ?? ""}>
              {urlLabel}
            </span>
            <span className="text-[10px] text-slate-500 flex-1 truncate" title={call.serviceName}>
              {call.serviceName}
            </span>
            <span className={`inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded border ${meta.cls}`}>
              <Icon size={10} /> {meta.label}
            </span>
            {ts && <span className="text-[10px] text-slate-600 font-mono">{ts}</span>}
            {open
              ? <ChevronDown size={12} className="text-slate-500 flex-shrink-0" />
              : <ChevronRight size={12} className="text-slate-500 flex-shrink-0" />}
          </div>
        </button>

        {open && (
          <div className="mt-2 ml-1 rounded-lg border border-slate-700 bg-slate-900 p-3 grid grid-cols-1 md:grid-cols-2 gap-3">
            <BodyPanel title="Request"  body={call.requestBody}  />
            <BodyPanel title="Response" body={call.responseBody} />
            <div className="md:col-span-2 text-[10px] font-mono text-slate-500 space-x-3">
              <span><span className="text-slate-600">pod:</span> {call.podName}</span>
              {call.correlationId && <span><span className="text-slate-600">corr:</span> {call.correlationId}</span>}
              {call.requestTimestamp && <span><span className="text-slate-600">time:</span> {call.requestTimestamp.replace("T", " ").slice(0, 23)}</span>}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

const FlowView = ({ calls, searchId }: { calls: DownstreamApiCall[]; searchId: string }) => {
  const hasError = calls.some(c => c.callStatus !== "SUCCESS")

  return (
    <div className="mt-2">
      {/* Root node — the correlation ID / journey entry point */}
      <div className="flex mb-1">
        <div className="flex flex-col items-center mr-3 flex-shrink-0">
          <div className="w-4 h-4 rounded-full bg-blue-500 border-2 border-blue-400 flex items-center justify-center mt-0.5">
            <div className="w-1.5 h-1.5 rounded-full bg-white" />
          </div>
          <div className="w-px flex-1 bg-slate-700 mt-1" style={{ minHeight: 20 }} />
        </div>
        <div className="flex-1 mb-2">
          <div className="bg-blue-900/30 border border-blue-700/50 rounded-lg px-3 py-2 flex items-center gap-2">
            <Network size={13} className="text-blue-400 flex-shrink-0" />
            <div>
              <div className="text-xs font-semibold text-blue-300">Journey Entry</div>
              <div className="text-[10px] font-mono text-blue-400/70 truncate max-w-xs">{searchId}</div>
            </div>
            <div className="ml-auto flex items-center gap-2 text-[10px]">
              <span className="text-green-400">{calls.filter(c => c.callStatus === "SUCCESS").length} ok</span>
              {hasError && <span className="text-red-400">{calls.filter(c => c.callStatus !== "SUCCESS").length} failed</span>}
              <span className="text-slate-500">{calls.length} calls</span>
            </div>
          </div>
        </div>
      </div>

      {/* Call nodes */}
      {calls.map((call, i) => (
        <FlowNode key={call.id} call={call} index={i} isLast={i === calls.length - 1} />
      ))}

      {/* Terminal node */}
      <div className="flex">
        <div className="flex flex-col items-center mr-3 flex-shrink-0">
          <div className={`w-3 h-3 rounded-full border-2 mt-1 ${
            hasError ? "border-red-400 bg-red-400/20" : "border-green-400 bg-green-400/20"
          }`} />
        </div>
        <div className="flex-1 mb-2">
          <div className={`rounded-lg border px-3 py-1.5 text-xs ${
            hasError
              ? "border-red-900/50 bg-red-950/20 text-red-400"
              : "border-green-900/50 bg-green-950/20 text-green-400"
          }`}>
            {hasError ? "Journey completed with errors" : "Journey completed successfully"}
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────
const DownstreamCallsViewer = ({ env }: Props) => {
  const [searchId, setSearchId] = useState("")
  const [serviceName, setServiceName] = useState("")
  const [timeRange, setTimeRange] = useState<string>("")
  const [data, setData] = useState<DownstreamCallsResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<"ALL" | "SUCCESS" | "ERROR">("ALL")
  const [viewMode, setViewMode] = useState<"list" | "flow">("flow")

  const handleSearch = async () => {
    if (!env) { setError("Select an environment first"); return }
    if (!searchId.trim()) { setError("Enter a correlation ID or transaction ID"); return }
    setLoading(true); setError(null); setData(null)
    try {
      const params = new URLSearchParams({ envName: env, searchId: searchId.trim() })
      if (serviceName.trim()) params.append("serviceName", serviceName.trim())
      if (timeRange) params.append("timeRangeMinutes", timeRange)

      const res = await fetch(`http://localhost:8080/api/journey-logs/downstream-calls?${params}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setData(await res.json())
    } catch (e: any) {
      setError(e?.message ?? "Search failed")
    } finally {
      setLoading(false)
    }
  }

  const handleDownload = () => {
    if (!data) return
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `downstream-calls-${data.searchId}-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const visible = (data?.calls ?? []).filter(c =>
    filter === "ALL" ? true
      : filter === "SUCCESS" ? c.callStatus === "SUCCESS"
      : c.callStatus !== "SUCCESS"
  )

  return (
    <div className="bg-slate-900 border border-slate-700 rounded-lg p-5 mt-6">
      <div className="flex items-center gap-2 mb-4">
        <Network size={18} className="text-purple-400" />
        <h3 className="text-white font-medium">Downstream API Calls</h3>
        <span className="text-xs text-slate-500">
          Reconstruct SOAP/REST hops for one journey by correlation ID
        </span>
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
          className="col-span-2 flex items-center justify-center gap-1 bg-purple-600 hover:bg-purple-500 disabled:bg-slate-700 disabled:text-slate-500 text-white text-sm rounded px-3 py-2"
        >
          <Search size={14} />
          {loading ? "Searching..." : "Trace"}
        </button>
      </div>

      {!env && (
        <p className="text-xs text-yellow-400">Select an environment in the control bar above to search.</p>
      )}
      {error && <p className="text-sm text-red-400">{error}</p>}

      {data && (
        <div className="mt-3">
          {/* stats + controls row */}
          <div className="flex items-center justify-between mb-3 text-sm flex-wrap gap-2">
            <div className="text-slate-300 space-x-3">
              <span><span className="text-slate-500">Calls:</span> {data.totalCalls}</span>
              <span className="text-green-400">{data.successCount} ok</span>
              <span className={data.errorCount > 0 ? "text-red-400" : "text-slate-500"}>
                {data.errorCount} error
              </span>
              <span className="text-slate-500">·</span>
              <span><span className="text-slate-500">Pods:</span> {data.totalPodsSearched}</span>
              <span><span className="text-slate-500">Env:</span> {data.environment}</span>
            </div>

            <div className="flex items-center gap-2">
              {/* view toggle */}
              <div className="flex bg-slate-800 border border-slate-700 rounded text-xs overflow-hidden">
                <button
                  onClick={() => setViewMode("flow")}
                  className={`flex items-center gap-1 px-2.5 py-1 ${viewMode === "flow" ? "bg-purple-600 text-white" : "text-slate-400 hover:text-white"}`}
                >
                  <GitBranch size={11} /> Flow
                </button>
                <button
                  onClick={() => setViewMode("list")}
                  className={`flex items-center gap-1 px-2.5 py-1 ${viewMode === "list" ? "bg-slate-700 text-white" : "text-slate-400 hover:text-white"}`}
                >
                  <List size={11} /> List
                </button>
              </div>

              {/* status filter (list mode only) */}
              {viewMode === "list" && (
                <div className="flex bg-slate-800 border border-slate-700 rounded text-xs overflow-hidden">
                  {(["ALL", "SUCCESS", "ERROR"] as const).map(f => (
                    <button
                      key={f}
                      onClick={() => setFilter(f)}
                      className={`px-2.5 py-1 ${filter === f ? "bg-slate-700 text-white" : "text-slate-400 hover:text-slate-200"}`}
                    >
                      {f.charAt(0) + f.slice(1).toLowerCase()}
                    </button>
                  ))}
                </div>
              )}

              <button
                onClick={handleDownload}
                disabled={data.totalCalls === 0}
                className="flex items-center gap-1 bg-slate-700 hover:bg-slate-600 disabled:bg-slate-800 disabled:text-slate-600 text-white text-xs px-3 py-1.5 rounded"
              >
                <Download size={12} /> JSON
              </button>
            </div>
          </div>

          {data.totalCalls === 0 ? (
            <div className="text-sm text-slate-500 bg-slate-800 rounded px-3 py-2">
              No downstream calls found for this correlation ID.
            </div>
          ) : viewMode === "flow" ? (
            <div className="max-h-[36rem] overflow-y-auto pr-1">
              <FlowView calls={data.calls} searchId={data.searchId} />
            </div>
          ) : visible.length === 0 ? (
            <div className="text-sm text-slate-500 bg-slate-800 rounded px-3 py-2">
              No calls match the current filter.
            </div>
          ) : (
            <div className="space-y-2 max-h-[32rem] overflow-y-auto pr-1">
              {visible.map((c, i) => <CallRow key={c.id} call={c} index={i} />)}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default DownstreamCallsViewer
