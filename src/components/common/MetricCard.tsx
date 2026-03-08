interface Props {
    label: string
    value: string | number
    subText?: string
    variant?: "normal" | "critical" | "warning" | "healthy"
  }
  
  const MetricCard = ({
    label,
    value,
    subText,
    variant = "normal"
  }: Props) => {
  
    const getStyles = () => {
  
      switch (variant) {
  
        case "critical":
          return {
            text: "text-red-400",
            border: "border-red-500/40",
            glow: "shadow-red-500/10"
          }
  
        case "warning":
          return {
            text: "text-yellow-400",
            border: "border-yellow-500/40",
            glow: "shadow-yellow-500/10"
          }
  
        case "healthy":
          return {
            text: "text-green-400",
            border: "border-green-500/40",
            glow: "shadow-green-500/10"
          }
  
        default:
          return {
            text: "text-white",
            border: "border-slate-700",
            glow: ""
          }
  
      }
  
    }
  
    const styles = getStyles()
  
    return (
  
      <div
        className={`bg-slate-800 p-5 rounded-xl border ${styles.border} shadow-sm ${styles.glow} transition`}
      >
  
        <p className="text-sm text-slate-400 mb-2">
          {label}
        </p>
  
        <p className={`text-3xl font-semibold ${styles.text}`}>
          {value}
        </p>
  
        {subText && (
          <p className="text-xs text-slate-500 mt-2">
            {subText}
          </p>
        )}
  
      </div>
  
    )
  
  }
  
  export default MetricCard