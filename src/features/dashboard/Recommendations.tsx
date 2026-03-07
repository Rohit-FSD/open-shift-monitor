import Card from "../../components/common/Card"
import { clusterMock } from "../../api/mock/clusterMock"
import { AlertTriangle } from "lucide-react"

const Recommendations = () => {

  const recommendations = clusterMock.recommendations

  return (
    <Card title="Recommendations">

      <div className="space-y-4">

        {recommendations.map((rec, index) => (

          <div
            key={index}
            className="flex items-start gap-3 bg-slate-800/50 p-3 rounded-lg border border-slate-700"
          >

            <AlertTriangle
              size={18}
              className="text-yellow-400 mt-1"
            />

            <p className="text-sm text-slate-300">
              {rec}
            </p>

          </div>

        ))}

      </div>

    </Card>
  )
}

export default Recommendations