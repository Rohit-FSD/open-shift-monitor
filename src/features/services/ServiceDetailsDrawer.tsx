import { X } from "lucide-react"
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
}

interface Service {
  name: string
  namespace: string
  replicas: number
  readyReplicas: number
  status: string
  pods: Pod[]
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

export default ServiceDetailsDrawer
