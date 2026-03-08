import Card from "../../components/common/Card"
import StatusBadge from "../../components/common/StatusBadge"
import { useState } from "react"
import ServiceDetailsDrawer from "../services/ServiceDetailsDrawer"
import useFetch from "../../hooks/useFetch"

const ServicesTable = () => {

  const { data, loading, error } = useFetch(
    "http://localhost:8080/api/deployments/status"
  )

  const [selectedService, setSelectedService] = useState<any>(null)

  if (loading) return <p>Loading services...</p>
  if (error) return <p>Failed to load services</p>

  const services = data || []

  const getVersion = (image: string) => {
    if (!image) return "N/A"
    const parts = image.split(":")
    return parts[1] || "N/A"
  }

  return (
    <>
      <Card title="Services">

        <table className="w-full text-left">

          <thead>
            <tr>
              <th>Service</th>
              <th>Version</th>
              <th>Pods</th>
              <th>Uptime</th>
              <th>SLA</th>
            </tr>
          </thead>

          <tbody>
            {services.map((service: any, index: number) => (
              <tr
                key={index}
                onClick={() => setSelectedService(service)}
              >
                <td>{service.name}</td>
                <td>{getVersion(service.image)}</td>
                <td>{service.readyReplicas}/{service.replicas}</td>
                <td>{service.readyReplicas === service.replicas ? "100%" : "0%"}</td>
                <td>
                  <StatusBadge status={service.status} />
                </td>
              </tr>
            ))}
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