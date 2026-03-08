import Card from "../../components/common/Card"
import StatusBadge from "../../components/common/StatusBadge"
import Loader from "../../components/common/Loader"
import useFetch from "../../hooks/useFetch"
import { useState } from "react"
import ServiceDetailsDrawer from "../services/ServiceDetailsDrawer"

interface Service {

  name: string
  image: string
  replicas: number
  readyReplicas: number
  status: string
  namespace: string
  pods: Pod[]

}

interface Pod {

  name: string
  node: string
  restarts: number
  containers: Container[]

}

interface Container {

  name: string
  image: string

}

const ServicesTable = () => {

  const { data, loading, error } = useFetch(
    "http://localhost:8080/api/deployments/status"
  )

  const [selectedService, setSelectedService] = useState<Service | null>(null)

  if (loading)
    return (
      <Card title="Services">
        <Loader text="Loading services..." />
      </Card>
    )

  if (error)
    return (
      <Card title="Services">
        <div className="text-red-400 py-6">
          Failed to load services
        </div>
      </Card>
    )

  if (!data || data.length === 0)
    return (
      <Card title="Services">
        <div className="text-slate-400 py-6">
          No services available
        </div>
      </Card>
    )

  const services = data as Service[]

  const getVersion = (image: string) => {

    if (!image) return "N/A"

    const parts = image.split(":")

    return parts[1] || "N/A"
  }

  return (

    <>
      <Card title="Services">

        <table className="w-full">

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
                  className="border-b border-slate-700 hover:bg-slate-800/30 cursor-pointer"
                  onClick={() => setSelectedService(service)}
                >

                  <td className="py-4">
                    {service.name}
                  </td>

                  <td>
                    {getVersion(service.image)}
                  </td>

                  <td>
                    {pods}
                  </td>

                  <td>
                    {uptime}
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