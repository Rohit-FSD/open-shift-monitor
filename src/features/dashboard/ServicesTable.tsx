import Card from "../../components/common/Card"
import StatusBadge from "../../components/common/StatusBadge"
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
}

interface Props {
  services: Service[]
}

const ServicesTable = ({ services }: Props) => {

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

    const parts = image.split(":")

    return parts.length > 1 ? parts[1] : "N/A"
  }

  const getStatusBorder = (status: string) => {

    switch (status?.toUpperCase()) {

      case "HEALTHY":
        return "border-l-4 border-green-500"

      case "WARNING":
        return "border-l-4 border-yellow-500"

      case "CRITICAL":
      case "BREACH":
        return "border-l-4 border-red-500"

      default:
        return "border-l-4 border-gray-600"

    }

  }

  return (

    <>
      <Card title="Services">

        <table className="w-full text-sm">

          <thead>

            <tr className="text-slate-400 border-b border-slate-700">

              <th className="py-3 text-left">Service</th>
              <th className="text-left">Version</th>
              <th className="text-left">Pods</th>
              <th className="text-left">Uptime</th>
              <th className="text-left">SLA</th>

            </tr>

          </thead>

          <tbody>

            {services.map((service: Service, index: number) => {

              const pods = `${service.readyReplicas}/${service.replicas}`

              const uptime =
                service.readyReplicas === service.replicas
                  ? "100%"
                  : "0%"

              return (

                <tr
                  key={index}
                  className={`border-b border-slate-700 hover:bg-slate-800/40 transition cursor-pointer ${getStatusBorder(service.status)}`}
                  onClick={() => setSelectedService(service)}
                >

                  <td className="py-4 font-medium text-slate-200">
                    {service.name}
                  </td>

                  <td className="text-slate-300">
                    {getVersion(service.image)}
                  </td>

                  <td className="text-slate-300">
                    {pods}
                  </td>

                  <td>

                    <span
                      className={
                        uptime === "100%"
                          ? "text-green-400 font-medium"
                          : "text-red-400 font-medium"
                      }
                    >
                      {uptime}
                    </span>

                  </td>

                  <td>
                    <StatusBadge status={service.status} />
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
          onClose={() => setSelectedService(null)}
        />

      )}

    </>
  )
}

export default ServicesTable