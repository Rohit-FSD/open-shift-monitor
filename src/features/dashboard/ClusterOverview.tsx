import MetricCard from "../../components/common/MetricCard"

interface Props {
  slaData: any
}

const ClusterOverview = ({ slaData }: Props) => {

  const summary = slaData?.overallSummary

  if (!summary)
    return (
      <div className="text-slate-400 py-4">
        No cluster overview data available
      </div>
    )

  const getVariant = () => {

    const status = summary.status?.toUpperCase()

    if (status === "CRITICAL") return "critical"

    if (status === "WARNING") return "warning"

    if (status === "HEALTHY") return "healthy"

    return "normal"

  }

  return (

    <div className="grid grid-cols-4 gap-4 mb-6">

      <MetricCard
        label="Avg Uptime"
        value={`${summary.averageUptime ?? 0}%`}
        subText="below SLA"
      />

      <MetricCard
        label="Downtime"
        value={summary.totalDowntimeFormatted ?? "0"}
        subText="this week"
      />

      <MetricCard
        label="Incidents"
        value={summary.totalIncidents ?? 0}
        subText="reported"
      />

      <MetricCard
        label="SLA Status"
        value={summary.status ?? "UNKNOWN"}
        variant={getVariant()}
      />

    </div>

  )
}

export default ClusterOverview