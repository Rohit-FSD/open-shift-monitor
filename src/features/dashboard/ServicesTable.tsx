import Card from "../../components/common/Card"
import { useState } from "react"
import ServiceDetailsDrawer from "../services/ServiceDetailsDrawer"

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
  image: string
  replicas: number
  readyReplicas: number
  status: string
  namespace: string
  pods: Pod[]
  containerVersions?: Record<string, string>
  revision?: number
  lastDeployedAt?: string
  lastUpdated?: string
}

interface Props {
  services: Service[]
  env: string
}

const ServicesTable = ({ services, env }: Props) => {

  const [selectedService, setSelectedService] = useState<Service | null>(null)

  if (!services || services.length === 0)
    return (
      <Card title="Services">
        <div className="text-slate-400 py-6">
          No services available
        </div>
      </Card>
    )

  const getVersion = (image: string) => {
    if (!image) return "N/A"
    const at = image.indexOf("@")
    if (at > 0) {
      const digest = image.slice(at + 1)
      const colon = digest.indexOf(":")
      const hash = colon > 0 ? digest.slice(colon + 1) : digest
      return "sha256:" + hash.slice(0, 12)
    }
    const lastColon = image.lastIndexOf(":")
    const lastSlash = image.lastIndexOf("/")
    if (lastColon < 0 || lastColon < lastSlash) return "latest"
    return image.slice(lastColon + 1)
  }

  const renderContainerVersions = (service: Service) => {
    const cv = service.containerVersions
    if (cv && Object.keys(cv).length > 0) {
      return (
        <div className="space-y-0.5">
          {Object.entries(cv).map(([name, version]) => (
            <div key={name} className="text-xs">
              <span className="text-slate-400">{name}: </span>
              <span className="text-white font-medium">{version}</span>
            </div>
          ))}
        </div>
      )
    }
    return <span className="text-xs text-slate-300">{getVersion(service.image)}</span>
  }

  return (

    <>
      <Card title="Services">

        <table className="w-full">

          <thead>
            <tr className="text-slate-400 border-b border-slate-700 text-sm">
              <th className="py-3 text-left">Service</th>
              <th className="text-left">Container Versions</th>
              <th className="text-left">Pods</th>
              <th className="text-left">Uptime</th>
              <th className="text-left">Pod Status</th>
            </tr>
          </thead>

          <tbody>

            {services.map((service: Service, index: number) => {

              const pods = `${service.readyReplicas}/${service.replicas}`

              const uptime =
                service.replicas > 0
                  ? `${Math.round((service.readyReplicas / service.replicas) * 100)}%`
                  : "0%"

              return (

                <tr
                  key={index}
                  className="border-b border-slate-700 hover:bg-slate-800/30 cursor-pointer"
                  onClick={() => setSelectedService(service)}
                >

                  <td className="py-4 text-white">
                    <div>{service.name}</div>
                    {service.lastDeployedAt && (
                      <div className="text-[10px] text-slate-500 mt-0.5">
                        Deployed {timeAgo(service.lastDeployedAt)}
                        {service.revision != null && ` · #${service.revision}`}
                      </div>
                    )}
                  </td>

                  <td className="py-4">
                    {renderContainerVersions(service)}
                  </td>

                  <td className="text-white">
                    {pods}
                  </td>

                  <td>
                    <span
                      className={
                        uptime === "100%"
                          ? "text-green-400"
                          : "text-red-400"
                      }
                    >
                      {uptime}
                    </span>
                  </td>

                  <td>
                    <div className="flex flex-wrap gap-1">
                      {service.pods && service.pods.length > 0 ? (
                        service.pods.map((pod, pi) => {
                          const running = pi < service.readyReplicas
                          return (
                            <span
                              key={pi}
                              title={pod.name + (running ? " · Running" : " · Not Running")}
                              className={`inline-block w-2.5 h-2.5 rounded-full ${running ? "bg-green-400" : "bg-red-400"}`}
                            />
                          )
                        })
                      ) : (
                        Array.from({ length: service.replicas }).map((_, pi) => (
                          <span
                            key={pi}
                            className={`inline-block w-2.5 h-2.5 rounded-full ${pi < service.readyReplicas ? "bg-green-400" : "bg-red-400"}`}
                          />
                        ))
                      )}
                    </div>
                    <div className="text-[10px] text-slate-500 mt-1">
                      {service.readyReplicas}/{service.replicas} running
                    </div>
                  </td>

                </tr>

              )
            })}

          </tbody>

        </table>

      </Card>

      {selectedService && (

        <ServiceDetailsDrawer
          service={selectedService}
          env={env}
          onClose={() => setSelectedService(null)}
        />

      )}
    </>
  )
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

export default ServicesTable