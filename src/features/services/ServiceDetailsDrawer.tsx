import { X } from "lucide-react"
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
  pods: Pod[]
}

interface Props {
  service: Service
  env: string
  onClose: () => void
}

const ServiceDetailsDrawer = ({ service, env, onClose }: Props) => {

  const pods: Pod[] = service?.pods || []
  const containers: Container[] = pods[0]?.containers || []

  const { data, loading } = useLogFailures(env, service?.name)

  const logData = data?.services?.[0] || {}

  const failures = logData?.failures || []
  const podFailures = logData?.podFailures || []
  const applicationErrors = logData?.applicationErrors || []

  const healthStatus = logData?.healthStatus || "UNKNOWN"

  const isDown =
    failures.length > 0 ||
    podFailures.length > 0 ||
    applicationErrors.length > 0 ||
    healthStatus === "DOWN"

  return (

    <div className="fixed inset-0 z-50 flex justify-end">

      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
      />

      <div className="relative w-[380px] h-full bg-slate-900 border-l border-slate-800 p-6 overflow-y-auto">

        <div className="flex items-center justify-between mb-6">

          <h2 className="text-lg font-semibold text-white">
            View Details
          </h2>

          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white"
          >
            <X size={20} />
          </button>

        </div>

        <div className="space-y-2 text-sm mb-6">

          <p>
            <span className="text-slate-400">Service:</span>{" "}
            {service.name}
          </p>

          <p>
            <span className="text-slate-400">Replicas:</span>{" "}
            {service.readyReplicas}/{service.replicas}
          </p>

        </div>

        {/* 🔥 HEALTH */}

        <div className="mb-6">

          <h3 className="text-sm font-semibold mb-2 text-slate-300">
            Health Status
          </h3>

          {loading ? (
            <p className="text-xs text-slate-400">
              Checking...
            </p>
          ) : (
            <div
              className={`p-3 rounded-md text-sm ${
                isDown
                  ? "bg-red-500/20 text-red-400"
                  : "bg-green-500/20 text-green-400"
              }`}
            >
              {isDown ? "🚨 DOWN" : "✅ HEALTHY"}
            </div>
          )}

        </div>

        {/* LOG FAILURES */}

        <div>

          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Log Failures
          </h3>

          {!loading &&
            failures.length === 0 &&
            podFailures.length === 0 &&
            applicationErrors.length === 0 && (
              <p className="text-xs text-slate-400">
                No issues detected
              </p>
            )}

          {failures.map((f: any, i: number) => (
            <div key={i} className="bg-slate-800 p-3 rounded mb-2">
              <p className="text-red-400 text-xs">
                {f.httpMethod} {f.endpoint}
              </p>
              <p className="text-xs text-slate-400">{f.errorType}</p>
            </div>
          ))}

        </div>

      </div>

    </div>

  )
}

export default ServiceDetailsDrawer