interface Props {
    label: string
    value: string | number
    subText?: string
    variant?: "normal" | "critical"
  }
  
  const MetricCard = ({ label, value, subText, variant = "normal" }: Props) => {
  
    const statusColor =
      variant === "critical"
        ? "text-red-400"
        : "text-white"
  
    return (
  
      <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
  
        <p className="text-sm text-slate-400 mb-1">
          {label}
        </p>
  
        <p className={`text-2xl font-semibold ${statusColor}`}>
          {value}
        </p>
  
        {subText && (
          <p className="text-xs text-slate-500 mt-1">
            {subText}
          </p>
        )}
  
      </div>
  
    )
  }
  
  export default MetricCard