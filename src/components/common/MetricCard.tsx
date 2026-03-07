interface Props {
    label: string
    value: string | number
    severity?: "normal" | "warning" | "critical"
    subtitle?: string
}

const MetricCard = ({ label, value, severity = "normal", subtitle }: Props) => {

    const colors = {
        normal: "text-white",
        warning: "text-yellow-400",
        critical: "text-red-400"
    }

    return (
        <div className="bg-slate-800 p-5 rounded-xl border border-slate-700">
            <p className="text-sm text-slate-400">{label}</p>
            <p className={`text-3xl font-bold mt-2 ${colors[severity]}`}>
                {value}
            </p>
            {subtitle && (
                <p className="text-xs text-slate-400 mt-1">{subtitle}</p>
            )}
        </div>
    )
}

export default MetricCard