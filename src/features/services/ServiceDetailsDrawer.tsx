import { X } from "lucide-react"

interface Props {
  service: any
  onClose: () => void
}

const ServiceDetailsDrawer = ({ service, onClose }: Props) => {
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
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-lg font-semibold tracking-wide">
            View Details
          </h2>

          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white transition"
          >
            <X size={18} />
          </button>
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
        <div className="mb-8">
          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Containers
          </h3>

          <div className="space-y-2">
            {service.containers?.map((c: any, i: number) => (
              <div
                key={i}
                className="bg-slate-800 border border-slate-700 p-3 rounded-lg flex justify-between items-center hover:border-slate-500 transition"
              >
                <span>{c.name}</span>
                <span className="text-slate-400">{c.version}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Pods */}
        <div>
          <h3 className="text-sm font-semibold mb-3 text-slate-300">
            Pods
          </h3>

          <div className="space-y-3">
            {service.podDetails?.length ? (
              service.podDetails.map((pod: any, i: number) => (
                <div
                  key={i}
                  className="bg-slate-800 border border-slate-700 p-4 rounded-lg space-y-1"
                >
                  <p className="font-medium">
                    <span className="h-2 w-2 bg-green-500 rounded-full inline-block mr-2"></span>
                    {pod.name}
                  </p>

                  <p className="text-xs text-slate-400">
                    Node: {pod.node}
                  </p>

                  <p className="text-xs text-slate-500">
                    Restarts: {pod.restarts}
                  </p>
                </div>
              ))
            ) : (
              <p className="text-sm text-slate-500">
                No pods running
              </p>
            )}
          </div>
        </div>

      </div>
    </div>
  )
}

export default ServiceDetailsDrawer