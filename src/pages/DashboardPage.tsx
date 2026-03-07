import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import ClusterOverview from "../features/dashboard/ClusterOverview"
import ServicesTable from "../features/dashboard/ServicesTable"
import SLAMetrics from "../features/dashboard/SLAMetrics"
import QAImpact from "../features/dashboard/QAImpact"
import Recommendations from "../features/dashboard/Recommendations"
import ControlBar from "../components/layout/ControlBar"

const DashboardPage = () => {

  return (
    <div className="flex">

      <Sidebar />

      <div className="flex-1">

        <Navbar />

        <div className="p-10 space-y-10">

          <ControlBar />

          <ClusterOverview />

          <ServicesTable />

          <SLAMetrics />

          <QAImpact />

          <Recommendations />

        </div>

      </div>

    </div>
  )
}

export default DashboardPage