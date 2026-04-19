import { useState } from "react"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import ControlBar from "../components/layout/ControlBar"
import useFetch from "../hooks/useFetch"
import useLogFailures, { ApplicationError, PodFailure, DownstreamFailure } from "../hooks/useLogFailures"
import { AlertTriangle, AlertCircle, Server, Activity, ChevronDown, ChevronUp, RefreshCw } from "lucide-react"

const severityColor: Record<string, string> = {
  HIGH: "text-red-400 bg-red-900/30",
  MEDIUM: "text-yellow-400 bg-yellow-900/30",
  LOW: "text-blue-400 bg-blue-900/30",
}

const healthBadge: Record<string, string> = {
  HEALTHY: "bg-green-600 text-white",
  DEGRADED: "bg-orange-500 text-white",
  DOWN: "bg-red-600 text-white",
}

const SectionAccordion = ({ title, count, icon, children }: {
  title: string; count: number; icon: React.ReactNode; children: React.ReactNode
}) => {
  const [open, setOpen] = useState(true)
  return (
    <div className="bg-slate-800 rounded-lg mb-4">
      <button
        className="w-full flex items-center justify-between px-4 py-3 text-left"
        onClick={() => setOpen(o => !o)}
      >
        <div className="flex items-center gap-2 text-white font-medium">
          {icon}{title}
          <span className="ml-2 text-xs bg-slate-700 px-2 py-0.5 rounded-full">{count}</span>
        </div>
        {open ? <ChevronUp size={16} className="text-slate-400" /> : <ChevronDown size={16} className="text-slate-400" />}
      </button>
      {open && <div className="px-4 pb-4 space-y-3">{children}</div>}
    </div>
  )
}

const AppErrorCard = ({ e }: { e: ApplicationError }) => (
  <div className="bg-slate-900 rounded-lg p-4 border border-slate-700">
    <div className="flex items-start justify-between mb-2">
      <div>
        <span className="text-white font-mono font-medium">{e.errorCode}</span>
        {e.endpoint && <span className="ml-2 text-xs text-slate-400">{e.endpoint}</span>}
      </div>
      <div className="flex items-center gap-2">
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${severityColor[e.severity] ?? severityColor.LOW}`}>
          {e.severity}
        </span>
        <span className="text-xs text-slate-400">{e.occurrences}x</span>
      </div>
    </div>
    <p className="text-sm text-slate-300 mb-2">{e.description}</p>
    <div className="bg-slate-800 rounded p-2 mb-2">
      <p className="text-xs text-green-400 font-medium mb-1">Recommendation</p>
      <p className="text-xs text-slate-300">{e.recommendedSolution}</p>
    </div>
    {e.steps?.length > 0 && (
      <ul className="text-xs text-slate-400 space-y-1 list-disc list-inside">
        {e.steps.map((s, i) => <li key={i}>{s}</li>)}
      </ul>
    )}
  </div>
)

const PodFailureCard = ({ p }: { p: PodFailure }) => (
  <div className="bg-slate-900 rounded-lg p-4 border border-slate-700">
    <div className="flex items-start justify-between mb-2">
      <span className="text-white font-mono text-sm">{p.podName}</span>
      <span className="text-xs bg-red-900/40 text-red-400 px-2 py-0.5 rounded-full">{p.reason}</span>
    </div>
    {p.message && <p className="text-xs text-slate-400 mb-2">{p.message}</p>}
    <p className="text-xs text-slate-300">Restarts: <span className="text-yellow-400">{p.restartCount}</span></p>
    <div className="bg-slate-800 rounded p-2 mt-2">
      <p className="text-xs text-green-400 font-medium mb-1">Recommendation</p>
      <p className="text-xs text-slate-300">{p.recommendedSolution}</p>
    </div>
  </div>
)

const DownstreamCard = ({ d }: { d: DownstreamFailure }) => (
  <div className="bg-slate-900 rounded-lg p-4 border border-slate-700">
    <div className="flex items-start justify-between mb-2">
      <div>
        <span className="text-white font-medium text-sm">{d.dependency}</span>
        <span className="ml-2 text-xs bg-slate-700 px-2 py-0.5 rounded-full text-slate-300">{d.protocol}</span>
      </div>
      <span className="text-xs text-slate-400">{d.occurrences}x</span>
    </div>
    {d.sampleError && <p className="text-xs text-red-300 mb-2 font-mono truncate">{d.sampleError}</p>}
    <div className="bg-slate-800 rounded p-2">
      <p className="text-xs text-green-400 font-medium mb-1">Recommendation</p>
      <p className="text-xs text-slate-300">{d.recommendedSolution}</p>
    </div>
  </div>
)

const ApplicationFailuresPage = () => {
  const [env, setEnv] = useState("")
  const [selectedService, setSelectedService] = useState("")
  const [timeRange, setTimeRange] = useState(60)
  const { data: servicesData } = useFetch<any[]>(env ? `http://localhost:8080/api/deployments/status/env/${env}` : null)
  const { data, loading, error, analyze, reset } = useLogFailures()

  const handleEnvChange = (e: string) => {
    setEnv(e)
    setSelectedService("")
    reset()
  }

  const handleAnalyze = () => {
    if (!env || !selectedService) return
    analyze({ envName: env, serviceName: selectedService, timeRangeMinutes: timeRange, includeSamples: true })
  }

  const services: string[] = servicesData?.map((s: any) => s.name).filter(Boolean) ?? []

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-7xl mx-auto w-full">
          <ControlBar onRefresh={() => {}} onEnvChange={(e) => handleEnvChange(e)} />

          <div className="mt-4 mb-6">
            <h1 className="text-2xl font-semibold text-white">Failure Analysis</h1>
            <p className="text-slate-400 text-sm mt-1">
              View all application errors, downstream failures, and pod failures across services
            </p>
          </div>

          {/* Controls */}
          <div className="flex gap-3 mb-6 flex-wrap">
            <select
              value={selectedService}
              onChange={e => setSelectedService(e.target.value)}
              className="bg-slate-800 border border-slate-700 text-white text-sm rounded px-3 py-2 min-w-[220px]"
            >
              <option value="">Select service...</option>
              {services.map(s => <option key={s} value={s}>{s}</option>)}
            </select>

            <select
              value={timeRange}
              onChange={e => setTimeRange(Number(e.target.value))}
              className="bg-slate-800 border border-slate-700 text-white text-sm rounded px-3 py-2"
            >
              <option value={30}>Last 30 min</option>
              <option value={60}>Last 1 hour</option>
              <option value={120}>Last 2 hours</option>
              <option value={360}>Last 6 hours</option>
              <option value={720}>Last 12 hours</option>
            </select>

            <button
              onClick={handleAnalyze}
              disabled={!env || !selectedService || loading}
              className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm px-4 py-2 rounded"
            >
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
              {loading ? "Analyzing..." : "Analyze"}
            </button>
          </div>

          {/* Error state */}
          {error && (
            <div className="bg-red-900/30 border border-red-700 rounded-lg p-4 mb-4 text-red-300 text-sm">{error}</div>
          )}

          {/* Empty state */}
          {!data && !loading && !error && (
            <div className="bg-slate-800/50 rounded-lg p-8 text-center">
              <AlertCircle size={40} className="text-slate-600 mx-auto mb-3" />
              <p className="text-slate-400">Select a service and click Analyze to view failure details</p>
            </div>
          )}

          {/* Results */}
          {data && (
            <>
              {/* Summary bar */}
              <div className="flex items-center gap-4 mb-6 p-4 bg-slate-800 rounded-lg flex-wrap">
                <div className="flex items-center gap-2">
                  <span className="text-slate-400 text-sm">Service:</span>
                  <span className="text-white font-medium">{data.serviceName}</span>
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${healthBadge[data.healthStatus] ?? ""}`}>
                    {data.healthStatus}
                  </span>
                </div>
                <div className="flex gap-4 ml-auto text-sm">
                  <span className="text-slate-400">Logs scanned: <span className="text-white">{data.summary.totalLogsScanned.toLocaleString()}</span></span>
                  <span className="text-red-400">App errors: <span className="text-white">{data.summary.applicationErrorCount}</span></span>
                  <span className="text-orange-400">Pod failures: <span className="text-white">{data.summary.podFailureCount}</span></span>
                  <span className="text-yellow-400">Downstream: <span className="text-white">{data.summary.downstreamFailureCount}</span></span>
                </div>
              </div>

              {data.summary.totalFailures === 0 && (
                <div className="bg-green-900/20 border border-green-700 rounded-lg p-6 text-center">
                  <p className="text-green-400 font-medium">No failures detected in the selected time range</p>
                </div>
              )}

              {data.podFailures?.length > 0 && (
                <SectionAccordion title="Pod Failures" count={data.podFailures.length} icon={<Server size={16} className="text-red-400" />}>
                  {data.podFailures.map((p, i) => <PodFailureCard key={i} p={p} />)}
                </SectionAccordion>
              )}

              {data.applicationErrors?.length > 0 && (
                <SectionAccordion title="Application Errors" count={data.applicationErrors.length} icon={<AlertCircle size={16} className="text-orange-400" />}>
                  {data.applicationErrors.map((e, i) => <AppErrorCard key={i} e={e} />)}
                </SectionAccordion>
              )}

              {data.downstreamFailures?.length > 0 && (
                <SectionAccordion title="Downstream Failures" count={data.downstreamFailures.length} icon={<Activity size={16} className="text-yellow-400" />}>
                  {data.downstreamFailures.map((d, i) => <DownstreamCard key={i} d={d} />)}
                </SectionAccordion>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}

export default ApplicationFailuresPage
