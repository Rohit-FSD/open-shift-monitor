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
  const { data, loading } = useLogFailures(service?.namespace, service?.name)

  const failures = data?.services?.[0]?.failures || []
  const podFailures = data?.services?.[0]?.podFailures || []
  const applicationErrors = data?.services?.[0]?.applicationErrors || []

  return (

    <div className="fixed inset-0 z-50 flex justify-end">

      {/* overlay */}

      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
      />

      {/* drawer */}

      <div className="relative w-[360px] h-full bg-slate-900 border-l border-slate-800 p-6 overflow-y-auto">

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

        <div className="mt-6">

          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Log Failures
          </h3>

          {loading && (
            <p className="text-xs text-slate-400">Loading failure data…</p>
          )}

          {!loading && failures.length === 0 && podFailures.length === 0 && applicationErrors.length === 0 && (
            <p className="text-xs text-slate-400">
              No log failures detected
            </p>
          )}

          {failures.map((f: any, i: number) => (
            <div
              key={`f-${i}`}
              className="bg-slate-800 border border-slate-700 p-3 rounded-md mb-2"
            >
              <p className="text-red-400 text-xs">
                {f.httpMethod} {f.endpoint}
              </p>
              <p className="text-xs text-slate-400">{f.errorType}</p>
              <p className="text-xs text-slate-500">
                {f.errorMessage?.substring(0, 120)}
              </p>
            </div>
          ))}

          {podFailures.length > 0 && (
            <>
              <h3 className="text-sm font-semibold mb-2 mt-4 text-slate-300">
                Pod Failures
              </h3>
              {podFailures.map((p: any, i: number) => (
                <div
                  key={`p-${i}`}
                  className="bg-slate-800 border border-slate-700 p-3 rounded-md mb-2"
                >
                  <p className="text-red-400 text-xs">{p.podName}</p>
                  <p className="text-xs text-slate-400">{p.failureType} – {p.reason}</p>
                  <p className="text-xs text-slate-500">{p.message?.substring(0, 120)}</p>
                </div>
              ))}
            </>
          )}

          {applicationErrors.length > 0 && (
            <>
              <h3 className="text-sm font-semibold mb-2 mt-4 text-slate-300">
                Application Errors
              </h3>
              {applicationErrors.map((a: any, i: number) => (
                <div
                  key={`a-${i}`}
                  className="bg-slate-800 border border-slate-700 p-3 rounded-md mb-2"
                >
                  <p className="text-red-400 text-xs">{a.exceptionType}</p>
                  <p className="text-xs text-slate-500">{a.message?.substring(0, 120)}</p>
                </div>
              ))}
            </>
          )}

        </div>

      </div>

    </div>

  )
}

export default ServiceDetailsDrawer