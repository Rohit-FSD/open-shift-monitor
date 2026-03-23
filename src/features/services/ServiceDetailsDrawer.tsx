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
  onClose: () => void
}

const ServiceDetailsDrawer = ({ service, onClose }: Props) => {

  const pods: Pod[] = service?.pods || []
  const containers: Container[] = pods[0]?.containers || []

  // 🔥 LOG FAILURE API CALL
  const { data, loading } = useLogFailures(
    service?.namespace,
    service?.name
  )

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

      {/* overlay */}
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
      />

      {/* drawer */}
      <div className="relative w-[380px] h-full bg-slate-900 border-l border-slate-800 p-6 overflow-y-auto">

        {/* HEADER */}
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

        {/* SERVICE INFO */}

        <div className="space-y-2 text-sm mb-6">

          <p>
            <span className="text-slate-400">Service:</span>{" "}
            {service.name}
          </p>

          <p>
            <span className="text-slate-400">Namespace:</span>{" "}
            {service.namespace}
          </p>

          <p>
            <span className="text-slate-400">Replicas:</span>{" "}
            {service.readyReplicas}/{service.replicas}
          </p>

        </div>

        {/* 🔥 HEALTH STATUS (NEW CORE FEATURE) */}

        <div className="mb-6">

          <h3 className="text-sm font-semibold mb-2 text-slate-300">
            Health Status
          </h3>

          {loading ? (
            <p className="text-xs text-slate-400">
              Checking service health...
            </p>
          ) : (
            <div
              className={`p-3 rounded-md text-sm font-medium ${
                isDown
                  ? "bg-red-500/20 text-red-400 border border-red-500/30"
                  : "bg-green-500/20 text-green-400 border border-green-500/30"
              }`}
            >
              {isDown ? "🚨 SERVICE DOWN" : "✅ HEALTHY"}
            </div>
          )}

        </div>

        {/* CONTAINERS */}

        <div className="mb-6">

          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Containers
          </h3>

          {containers.length === 0 && (
            <p className="text-xs text-slate-400">
              No containers found
            </p>
          )}

          {containers.map((container, index) => {

            const version = container.image?.split(":")[1] || "N/A"

            return (

              <div
                key={index}
                className="bg-slate-800 border border-slate-700 p-3 rounded-md flex justify-between mb-2"
              >

                <span>{container.name}</span>

                <span className="text-green-400">
                  {version}
                </span>

              </div>

            )

          })}

        </div>

        {/* PODS */}

        <div>

          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Pods
          </h3>

          {pods.length === 0 && (
            <p className="text-xs text-slate-400">
              No pods available
            </p>
          )}

          {pods.map((pod, index) => (

            <div
              key={index}
              className="bg-slate-800 border border-slate-700 p-3 rounded-md mb-2"
            >

              <p>{pod.name}</p>

              <p className="text-xs text-slate-400">
                Node: {pod.node}
              </p>

              <p className="text-xs text-slate-500">
                Restarts: {pod.restarts || 0}
              </p>

            </div>

          ))}

        </div>

        {/* 🔥 LOG FAILURES */}

        <div className="mt-6">

          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Log Failures
          </h3>

          {!loading &&
            failures.length === 0 &&
            podFailures.length === 0 &&
            applicationErrors.length === 0 && (
              <p className="text-xs text-slate-400">
                No issues detected 🎉
              </p>
            )}

          {/* API FAILURES */}

          {failures.map((f: any, i: number) => (
            <div
              key={`f-${i}`}
              className="bg-slate-800 border border-red-500/30 p-3 rounded-md mb-2"
            >
              <p className="text-red-400 text-xs">
                {f.httpMethod} {f.endpoint}
              </p>
              <p className="text-xs text-slate-400">{f.errorType}</p>
            </div>
          ))}

          {/* POD FAILURES */}

          {podFailures.map((p: any, i: number) => (
            <div
              key={`p-${i}`}
              className="bg-slate-800 border border-red-500/30 p-3 rounded-md mb-2"
            >
              <p className="text-red-400 text-xs">{p.podName}</p>
              <p className="text-xs text-slate-400">
                {p.failureType} - {p.reason}
              </p>
            </div>
          ))}

          {/* APP ERRORS */}

          {applicationErrors.map((a: any, i: number) => (
            <div
              key={`a-${i}`}
              className="bg-slate-800 border border-red-500/30 p-3 rounded-md mb-2"
            >
              <p className="text-red-400 text-xs">
                {a.exceptionType}
              </p>
              <p className="text-xs text-slate-500">
                {a.message?.substring(0, 100)}
              </p>
            </div>
          ))}

        </div>

      </div>

    </div>

  )
}

export default ServiceDetailsDrawer