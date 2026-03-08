import Navbar from "../components/layout/Navbar"
import Sidebar from "../components/layout/Sidebar"
import ControlBar from "../components/layout/ControlBar"

import ClusterOverview from "../features/dashboard/ClusterOverview"
import ServicesTable from "../features/dashboard/ServicesTable"
import SLAMetrics from "../features/dashboard/SLAMetrics"
import QAImpact from "../features/dashboard/QAImpact"
import Recommendations from "../features/dashboard/Recommendations"

import useFetch from "../hooks/useFetch"
import Loader from "../components/common/Loader"

const DashboardPage = () => {

  const {
    data: slaData,
    loading: slaLoading
  } = useFetch(
    "http://localhost:8080/api/sla/report/current-week",
    30000
  )
  
  const {
    data: servicesData,
    loading: servicesLoading
  } = useFetch(
    "http://localhost:8080/api/deployments/status",
    30000
  )

  if (slaLoading || servicesLoading)
    return <Loader text="Loading dashboard..." />

  return (

    <div className="flex h-screen bg-slate-950 text-white">

      <Sidebar />

      <div className="flex flex-col flex-1 overflow-hidden">

        <Navbar />

        <ControlBar />

        <main className="flex-1 overflow-y-auto p-6 space-y-6">

          <ClusterOverview slaData={slaData} />

          <ServicesTable services={servicesData || []} />

          <div className="grid grid-cols-3 gap-6">

            <SLAMetrics data={slaData} />

            <QAImpact data={slaData} />

            <Recommendations data={slaData} />

          </div>

        </main>

      </div>

    </div>
  )
}

export default DashboardPage