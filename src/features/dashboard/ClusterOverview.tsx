import useFetch from "../../hooks/useFetch"
import MetricCard from "../../components/common/MetricCard"
import Loader from "../../components/common/Loader"

interface OverallSummary {
  averageUptime: number
  totalDowntimeFormatted: string
  totalIncidents: number
  status: string
}

interface SLAResponse {
  overallSummary: OverallSummary
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

  if (!data?.overallSummary)
    return (
      <div className="text-slate-400 py-6">
        No cluster overview data available
      </div>
    )

  const summary: OverallSummary = data.overallSummary

  return (

    <div className="grid grid-cols-4 gap-4">

      <MetricCard
        label="Avg Uptime"
        value={`${summary.averageUptime}%`}
        subText="below SLA"
      />

      <MetricCard
        label="Downtime"
        value={summary.totalDowntimeFormatted}
        subText="this week"
      />

      <MetricCard
        label="Incidents"
        value={summary.totalIncidents}
        subText="reported"
      />

      <MetricCard
        label="SLA Status"
        value={summary.status}
        variant="critical"
      />

    </div>

  )
}

export default ClusterOverview