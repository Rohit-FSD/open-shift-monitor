import MetricCard from "../../components/common/MetricCard"
import useLogFailures from "../../hooks/useLogFailures"

interface Props {
  slaData: any
}

const ClusterOverview = ({ slaData }: Props) => {

  const summary = slaData?.overallSummary

  const { data } = useLogFailures()

  const failures = data?.services?.[0]?.failures || []

  const envStatus =
    failures.length > 0 ? "CRITICAL" : "HEALTHY"

  if (!summary)
    return (
      <div className="text-slate-400">
        No cluster overview data available
      </div>
    )

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
        value={envStatus}
        subText={
          failures.length > 0
            ? `${failures.length} log failures detected`
            : "No log failures"
        }
        variant={envStatus === "CRITICAL" ? "critical" : "normal"}
      />

    </div>

  )
}

export default ClusterOverview