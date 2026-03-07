import Card from "../../components/common/Card"
import StatusBadge from "../../components/common/StatusBadge"
import { servicesMock } from "../../api/mock/servicesMock"
import { useState } from "react"
import ServiceDetailsDrawer from "../services/ServiceDetailsDrawer"

const ServicesTable = () => {

  const services = servicesMock
  const [selectedService, setSelectedService] = useState<any>(null)

  return (
    <>
      <Card title="Services">

        <table className="w-full text-left">

          <thead>
            <tr className="text-slate-400 border-b border-slate-700">
              <th className="py-3">Service</th>
              <th>Version</th>
              <th>Pods</th>
              <th>Uptime</th>
              <th>SLA</th>
            </tr>
          </thead>

          <tbody>

            {services.map((service, index) => (
              <tr
                key={index}
                onClick={() => setSelectedService(service)}
                className="border-b border-slate-700 hover:bg-slate-800/40 cursor-pointer transition"
              >

                <td
                  className={`py-4 pl-4 ${
                    service.sla === "BREACH"
                      ? "border-l-4 border-red-500 rounded-l"
                      : "border-l-4 border-transparent"
                  }`}
                >
                  {service.service}
                </td>

                <td className="py-4">{service.version}</td>

                <td className="py-4">{service.pods}</td>

                <td className={`py-4 ${service.uptime === "0%" ? "text-red-400 font-semibold" : ""}`}>
                  {service.uptime}
                </td>

                <td className="py-4">
                  <StatusBadge status={service.sla} />
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