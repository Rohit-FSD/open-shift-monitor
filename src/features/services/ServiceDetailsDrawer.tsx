import { X } from "lucide-react"

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

  const pods: Pod[] = service.pods || []

  const containers: Container[] = pods[0]?.containers || []

  return (

    <div className="fixed right-0 top-0 w-[360px] h-full bg-slate-900 border-l border-slate-700 p-6">

      <div className="flex justify-between items-center mb-6">

        <h2 className="text-lg font-semibold">View Details</h2>

        <button onClick={onClose}>
          <X size={18} />
        </button>

      </div>

      <div className="space-y-2 text-sm mb-6">

        <p>
          <span className="text-slate-400">Service:</span> {service.name}
        </p>

        <p>
          <span className="text-slate-400">Namespace:</span> {service.namespace}
        </p>

        <p>
          <span className="text-slate-400">Replicas:</span>{" "}
          {service.replicas} Ready: {service.readyReplicas}
        </p>

      </div>

      <div className="mb-6">

        <h3 className="text-sm font-semibold mb-3 text-slate-300">
          Containers
        </h3>

        {containers.length === 0 && (
          <p className="text-xs text-slate-400">
            No containers found
          </p>
        )}

        {containers.map((container: Container, index: number) => {

          const version = container.image?.split(":")[1]

          return (

            <div
              key={index}
              className="bg-slate-800 border border-slate-700 p-3 rounded-md flex justify-between mb-2"
            >

              <span>{container.name}</span>

              <span className="text-green-400">{version}</span>

            </div>

          )
        })}

      </div>

      <div>

        <h3 className="text-sm font-semibold mb-3 text-slate-300">
          Pods
        </h3>

        {pods.length === 0 && (
          <p className="text-xs text-slate-400">
            No pods available
          </p>
        )}

        {pods.map((pod: Pod, index: number) => (

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

    </div>

  )
}

export default ServiceDetailsDrawer