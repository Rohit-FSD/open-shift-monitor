interface Props {
    status?: string
  }
  
  const StatusBadge = ({ status }: Props) => {
  
    const safeStatus = status?.toLowerCase() || "unknown"
  
    const colors: Record<string, string> = {
      healthy: "bg-green-500",
      breach: "bg-red-500/90",
      warning: "bg-yellow-500",
      critical: "bg-red-600",
      unknown: "bg-gray-500"
    }
  
    return (
      <span
        className={`px-3 py-1 rounded-full text-xs font-semibold text-white ${colors[safeStatus]}`}
      >
        {status || "UNKNOWN"}
      </span>
    )
  }
  
  export default StatusBadge