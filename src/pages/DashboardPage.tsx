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
    "http://localhost:8080/api/sla/report/current-week"
  )

  const {
    data: servicesData,
    loading: servicesLoading
  } = useFetch(
    "http://localhost:8080/api/deployments/status"
  )

  if (slaLoading || servicesLoading)
    return <Loader text="Loading dashboard..." />

  return (

    <div className="space-y-6">

      <ClusterOverview slaData={slaData} />

      <ServicesTable services={servicesData || []} />

      <div className="grid grid-cols-3 gap-6">

        <SLAMetrics data={slaData} />

        <QAImpact data={slaData} />

        <Recommendations data={slaData} />

      </div>

    </div>
  )
}

export default DashboardPage