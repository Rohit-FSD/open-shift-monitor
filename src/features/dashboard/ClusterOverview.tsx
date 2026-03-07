import MetricCard from "../../components/common/MetricCard"
import { clusterMock } from "../../api/mock/clusterMock"

const ClusterOverview = () => {

    const data = clusterMock

    return (
        <div className="grid grid-cols-4 gap-8">

            <MetricCard
                label="Avg Uptime"
                value="3.64%"
                severity="critical"
                subtitle="↓ below SLA"
            />

            <MetricCard
                label="Total Downtime"
                value={data.totalDowntimeFormatted}
                severity="critical"
            />

            <MetricCard
                label="Incidents"
                value={data.totalIncidents}
            />

            <MetricCard
                label="SLA Status"
                value={data.status}
                severity={data.status === "Critical" ? "critical" : "normal"}
            />

        </div>
    )
}

export default ClusterOverview