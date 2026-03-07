import Card from "../../components/common/Card"
import MetricCard from "../../components/common/MetricCard"
import { clusterMock } from "../../api/mock/clusterMock"

const QAImpact = () => {

  const impact = clusterMock.testingImpact

  return (
    <Card title="QA Impact">

      <div className="grid grid-cols-4 gap-6">

        <MetricCard
          label="QA Hours Lost"
          value={`${impact.totalQAHoursLost} hrs`}
        />

        <MetricCard
          label="Tests Failed"
          value={impact.totalTestsFailed}
        />

        <MetricCard
          label="Engineers Impacted"
          value={impact.totalQAEngineersImpacted}
        />

        <MetricCard
          label="Cost Impact"
          value={`$${impact.estimatedCostImpact}`}
        />

      </div>

    </Card>
  )
}

export default QAImpact