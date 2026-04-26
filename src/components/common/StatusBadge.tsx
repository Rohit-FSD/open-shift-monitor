interface Props {
  status: string
}

const StatusBadge = ({ status }: Props) => {

  const getColor = () => {

    switch (status?.toUpperCase()) {

      case "HEALTHY":
        return "bg-green-500/20 text-green-400 border-green-500/40"

      case "WARNING":
      case "DEGRADED":
        return "bg-yellow-500/20 text-yellow-400 border-yellow-500/40"

      case "CRITICAL":
      case "BREACH":
      case "DOWN":
        return "bg-red-500/20 text-red-400 border-red-500/40"

      case "UNKNOWN":
        return "bg-slate-500/20 text-slate-300 border-slate-500/40"

      default:
        return "bg-gray-500/20 text-gray-400 border-gray-500/40"

    }

  }

  return (

    <span
      className={`px-2 py-1 text-xs rounded border ${getColor()}`}
    >
      {status}
    </span>

  )

}

export default StatusBadge