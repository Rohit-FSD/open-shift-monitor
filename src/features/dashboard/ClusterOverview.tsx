import MetricCard from "../../components/common/MetricCard"
import useFetch from "../../hooks/useFetch"

const ClusterOverview = () => {

  const { data, loading, error } = useFetch(
    "http://localhost:8080/api/deployments/status"
  )

  if (loading) return <p className="text-slate-400">Loading cluster status...</p>

  if (error)
    return <p className="text-red-400">Failed to load cluster data</p>

  const deployments = data || []

  return (
    <div className="grid grid-cols-3 gap-4">

      {deployments.map((deployment: any, index: number) => (
        <MetricCard
          key={index}
          label={deployment.name}
          value={deployment.status}
        />
      ))}

    </div>
  )
}

export default ClusterOverview