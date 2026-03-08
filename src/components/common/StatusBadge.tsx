interface Props {
  status: string
}

const StatusBadge = ({ status }: Props) => {

  const colorMap: any = {
    HEALTHY: "bg-green-500/20 text-green-400",
    DEGRADED: "bg-yellow-500/20 text-yellow-400",
    CRITICAL: "bg-red-500/20 text-red-400",
    UNKNOWN: "bg-gray-500/20 text-gray-400"
  }

  return (

    <span
      className={`px-2 py-1 text-xs rounded ${colorMap[status]}`}
    >
      {status}
    </span>

  )
}

export default StatusBadge