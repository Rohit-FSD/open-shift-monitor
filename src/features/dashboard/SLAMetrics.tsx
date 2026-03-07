import Card from "../../components/common/Card"
import { clusterMock } from "../../api/mock/clusterMock"

import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    Tooltip,
    ResponsiveContainer,
    Cell,
    CartesianGrid
} from "recharts"

const SLAMetrics = () => {

    const data = clusterMock.dailyBreakdown

    return (
        <Card title="Downtime This Week">

            <div className="h-72 mt-4">

                <ResponsiveContainer width="100%" height="100%">

                    <BarChart data={data}>

                        <CartesianGrid strokeDasharray="3 3" stroke="#334155" />

                        <XAxis dataKey="day" stroke="#94a3b8" />
                        <YAxis stroke="#94a3b8" />

                        <Tooltip
                            cursor={{ fill: "transparent" }}
                            contentStyle={{
                                backgroundColor: "#1e293b",
                                border: "1px solid #334155",
                                borderRadius: "6px",
                                color: "#e2e8f0"
                            }}
                        />

                        <Bar dataKey="downtime">
                            {data.map((entry, index) => (
                                <Cell
                                    key={index}
                                    fill={entry.downtime > 580 ? "#ef4444" : "#facc15"}
                                />
                            ))}
                        </Bar>

                    </BarChart>

                </ResponsiveContainer>

            </div>

        </Card>
    )
}

export default SLAMetrics