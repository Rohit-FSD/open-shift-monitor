import { Clock, GitCommit, History, X } from "lucide-react"
import { useEffect } from "react"
import useLogFailures from "../../hooks/useLogFailures"

interface Container {
  name: string
  image: string
}

interface Pod {
  name: string
  node: string
  restarts: number
  containers: Container[]
  created?: string
  startedAt?: string
}

interface Service {
  name: string
  namespace: string
  replicas: number
  readyReplicas: number
  status: string
  pods: Pod[]
  revision?: number
  lastDeployedAt?: string
  lastUpdated?: string
  containerVersions?: Record<string, string>
  previousRevision?: number
  previousVersion?: string
  previousContainerVersions?: Record<string, string>
  previousDeployedAt?: string
}

interface Props {
  service: Service
  env: string
  onClose: () => void
}

const healthStyle: Record<string, string> = {
  HEALTHY: "bg-green-500/20 text-green-400",
  DEGRADED: "bg-orange-500/20 text-orange-400",
  DOWN: "bg-red-500/20 text-red-400",
}

const ServiceDetailsDrawer = ({ service, env, onClose }: Props) => {
  const pods: Pod[] = service?.pods || []

  const { data, loading, analyze } = useLogFailures()

  useEffect(() => {
    if (env && service?.name) {
      analyze({ envName: env, serviceName: service.name, timeRangeMinutes: 60, includeSamples: false })
    }
  }, [env, service?.name, analyze])

  const deriveStatus = (): string => {
    if (!service.replicas || service.readyReplicas === 0) return "DOWN"
    if (service.readyReplicas < service.replicas) return "DEGRADED"
    const logFailures = (data?.applicationErrors?.length ?? 0) +
      (data?.podFailures?.length ?? 0) +
      (data?.downstreamFailures?.length ?? 0)
    if (logFailures > 0) return "DEGRADED"
    return "HEALTHY"
  }

  const status = deriveStatus()
  const applicationErrors = data?.applicationErrors || []
  const podFailures = data?.podFailures || []
  const downstreamFailures = data?.downstreamFailures || []
  const totalFailures = applicationErrors.length + podFailures.length + downstreamFailures.length

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />

      <div className="relative w-[420px] h-full bg-slate-900 border-l border-slate-800 p-6 overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-semibold text-white">View Details</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <X size={20} />
          </button>
        </div>

        <div className="space-y-2 text-sm mb-6">
          <p>
            <span className="text-slate-400">Service:</span> {service.name}
          </p>
          <p>
            <span className="text-slate-400">Replicas:</span> {service.readyReplicas}/{service.replicas}
          </p>
        </div>

        {(service.lastDeployedAt || service.revision != null) && (
          <div className="mb-6 bg-slate-800/60 border border-slate-700 rounded p-3 space-y-2">
            <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
              <Clock size={14} className="text-blue-400" />
              Last Deployed
            </h3>
            {service.lastDeployedAt && (
              <div className="text-xs">
                <div className="text-white">{formatDateTime(service.lastDeployedAt)}</div>
                <div className="text-slate-400">{timeAgo(service.lastDeployedAt)}</div>
              </div>
            )}
            {service.revision != null && (
              <div className="text-xs flex items-center gap-1.5 text-slate-400">
                <GitCommit size={11} />
                Revision <span className="text-slate-200 font-mono">#{service.revision}</span>
              </div>
            )}
            {service.lastUpdated && service.lastUpdated !== service.lastDeployedAt && (
              <div className="text-[11px] text-slate-500">
                Status updated {timeAgo(service.lastUpdated)}
              </div>
            )}
          </div>
        )}

        {(service.previousVersion || service.previousDeployedAt || service.previousRevision != null) && (
          <div className="mb-6 bg-slate-800/40 border border-slate-700 rounded p-3 space-y-2">
            <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
              <History size={14} className="text-amber-400" />
              Previous Version
            </h3>
            {service.previousContainerVersions && Object.keys(service.previousContainerVersions).length > 0 ? (
              <div className="space-y-0.5 text-xs">
                {Object.entries(service.previousContainerVersions).map(([name, ver]) => (
                  <div key={name}>
                    <span className="text-slate-400">{name}: </span>
                    <span className="text-white font-mono">{ver}</span>
                  </div>
                ))}
              </div>
            ) : service.previousVersion ? (
              <div className="text-xs text-white font-mono">{service.previousVersion}</div>
            ) : null}
            {service.previousDeployedAt && (
              <div className="text-[11px] text-slate-400">
                Deployed {formatDateTime(service.previousDeployedAt)} ({timeAgo(service.previousDeployedAt)})
              </div>
            )}
            {service.previousRevision != null && (
              <div className="text-[11px] text-slate-500 flex items-center gap-1">
                <GitCommit size={10} />
                Revision <span className="font-mono text-slate-400">#{service.previousRevision}</span>
              </div>
            )}
          </div>
        )}

        {pods[0]?.containers?.length > 0 && (
          <div className="mb-6">
            <h3 className="text-sm font-semibold mb-2 text-slate-300">Container Versions</h3>
            <div className="space-y-1">
              {pods[0].containers.map((c, i) => (
                <div key={i} className="flex justify-between text-xs bg-slate-800 px-3 py-2 rounded">
                  <span className="text-slate-300">{c.name}</span>
                  <span className="text-slate-400 font-mono">
                    {c.image?.split(":")[1] || "N/A"}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mb-6">
          <h3 className="text-sm font-semibold mb-2 text-slate-300">Health Status</h3>
          <div className={`p-3 rounded-md text-sm font-medium ${healthStyle[status] ?? "bg-slate-800 text-slate-400"}`}>
            {status}
          </div>
        </div>

        {pods.length > 0 && (
          <div className="mb-6">
            <h3 className="text-sm font-semibold mb-2 text-slate-300">Pod Details</h3>
            {pods.map((p, i) => (
              <div key={i} className="bg-slate-800 rounded p-3 mb-2 text-xs">
                <div className="text-white font-mono mb-1">{p.name}</div>
                <div className="text-slate-400">Node: {p.node}</div>
                <div className="text-slate-400">Restarts: {p.restarts}</div>
                {p.startedAt && (
                  <div className="text-slate-400">
                    Started: {timeAgo(p.startedAt)}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        <div>
          <h3 className="text-sm font-semibold mb-2 text-slate-300">Log Failures</h3>
          {loading && <p className="text-xs text-slate-400">Analyzing logs...</p>}
          {!loading && totalFailures === 0 && (
            <p className="text-xs text-slate-400">No issues detected</p>
          )}
          {!loading && totalFailures > 0 && (
            <div className="space-y-1 text-xs">
              {applicationErrors.length > 0 && (
                <div className="text-red-400">{applicationErrors.length} application errors</div>
              )}
              {podFailures.length > 0 && (
                <div className="text-orange-400">{podFailures.length} pod failures</div>
              )}
              {downstreamFailures.length > 0 && (
                <div className="text-yellow-400">{downstreamFailures.length} downstream failures</div>
              )}
              <p className="text-slate-500 mt-2">See Failure Analysis on the dashboard for details.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function formatDateTime(iso: string): string {
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleString(undefined, {
    year: "numeric", month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  })
}

function timeAgo(iso: string): string {
  const t = new Date(iso).getTime()
  if (isNaN(t)) return ""
  const mins = Math.floor((Date.now() - t) / 60000)
  if (mins < 1) return "just now"
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  const days = Math.floor(hrs / 24)
  if (days < 30) return `${days}d ago`
  const months = Math.floor(days / 30)
  return months < 12 ? `${months}mo ago` : `${Math.floor(months / 12)}y ago`
}

export default ServiceDetailsDrawer
