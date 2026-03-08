import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import ControlBar from "../components/layout/ControlBar"

import ClusterOverview from "../features/dashboard/ClusterOverview"

import useFetch from "../hooks/useFetch"
import ServicesTable from "../features/dashboard/ServicesTable"
import QAImpact from "../features/dashboard/QAImpact"
import Recommendations from "../features/dashboard/Recommendations"
import SLAMetrics from "../features/dashboard/SLAMetrics"

const DashboardPage = () => {

  const {
    data: slaData,
    refetch: refreshSla
  } = useFetch("http://localhost:8080/api/sla/report/current-week")

  const {
    data: servicesData,
    refetch: refreshServices
  } = useFetch("http://localhost:8080/api/deployments/status")

  const refreshAll = () => {

    refreshSla()

    refreshServices()

  }

  return (

    <div className="flex h-screen bg-slate-900 text-white">

      <Sidebar />

      <div className="flex-1 flex flex-col">

        <Navbar />

        <div className="p-6 max-w-7xl mx-auto w-full">

          <ControlBar onRefresh={refreshAll} />

          <ClusterOverview slaData={slaData} />

          <div className="mt-6">

            <ServicesTable services={servicesData || []} />

          </div>

          <div className="grid grid-cols-3 gap-6 mt-6">

            <SLAMetrics data={slaData} />

            <QAImpact data={slaData} />

            <Recommendations data={slaData} />

          </div>

        </div>

      </div>

    </div>

  )

}

export default DashboardPage