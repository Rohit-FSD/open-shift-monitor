import { X } from "lucide-react"

interface Props {
  service: any
  onClose: () => void
}

const ServiceDetailsDrawer = ({ service, onClose }: Props) => {
  const pods = service?.pods || []
  const containers = pods[0]?.containers || []

  if (!service) return null

  return (
    <div className="fixed inset-0 z-50 flex justify-end">

      {/* Overlay */}
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
      />

      {/* Drawer */}
      <div className="relative h-full w-[380px] bg-slate-900 border-l border-slate-700 p-6 shadow-2xl overflow-y-auto">

        {/* Header */}
        <div className="text-sm text-slate-400">
          Namespace: {service.namespace}
        </div>

        <div className="text-sm text-slate-400">
          Replicas: {service.replicas} Ready: {service.readyReplicas}
        </div>

        {/* Service Info */}
        <div className="space-y-2 text-sm mb-8">
          <p>
            <span className="text-slate-400">Service:</span> {service.service}
          </p>

          <p>
            <span className="text-slate-400">Namespace:</span> {service.namespace}
          </p>

          <p>
            <span className="text-slate-400">Replicas:</span> {service.replicas}
          </p>
        </div>

        {/* Containers */}
        <div className="space-y-2">

          {containers.map((container: any, index: number) => {

            const version = container.image?.split(":")[1] || "N/A"

            return (
              <div
                key={index}
                className="flex justify-between text-sm bg-slate-800/40 px-3 py-2 rounded"
              >
                <span>{container.name}</span>
                <span className="text-green-400">{version}</span>
              </div>
            )
          })}

        </div>

        {/* Pods */}
        <div className="space-y-3">

          {pods.map((pod: any, index: number) => (

            <div
              key={index}
              className="bg-slate-800/40 p-3 rounded"
            >

              <div className="flex justify-between text-sm">
                <span>{pod.name}</span>
                <span className="text-green-400">{pod.status}</span>
              </div>

              <div className="text-xs text-slate-400 mt-2">
                Restarts: {pod.restarts || 0}
              </div>

              <div className="text-xs text-slate-400">
                Node: {pod.node}
              </div>

            </div>

          ))}

        </div>

      </div>
    </div>
  )
}

export default ServiceDetailsDrawer