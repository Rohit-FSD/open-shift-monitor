import useFetch from "../../hooks/useFetch"
import MetricCard from "../../components/common/MetricCard"
import Loader from "../../components/common/Loader"

interface SLAData {
  averageUptime: number
  totalDowntimeFormatted: string
  totalIncidents: number
  status: string
}

const ClusterOverview = () => {

  const { data, loading, error } = useFetch(
    "http://localhost:8080/api/sla/report/current-week"
  )

  if (loading)
    return <Loader text="Loading cluster overview..." />

  if (error)
    return (
      <div className="text-red-400 py-6">
        Failed to load cluster overview
      </div>
    )

  if (!data)
    return (
      <div className="text-slate-400 py-6">
        No cluster overview data available
      </div>
    )

  const slaData = data as SLAData

  return (

    <div className="grid grid-cols-4 gap-4">

      <MetricCard
        label="Avg Uptime"
        value={`${slaData.averageUptime}%`}
        subText="below SLA"
      />

      <MetricCard
        label="Downtime"
        value={slaData.totalDowntimeFormatted}
        subText="this week"
      />

      <MetricCard
        label="Incidents"
        value={slaData.totalIncidents}
        subText="reported"
      />

      <MetricCard
        label="SLA Status"
        value={slaData.status}
        variant="critical"
      />

    </div>

  )
}

export default ClusterOverview