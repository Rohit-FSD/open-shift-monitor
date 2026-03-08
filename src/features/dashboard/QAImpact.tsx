import Card from "../../components/common/Card"

interface Props {
  data: any
}

const QAImpact = ({ data }: Props) => {

  const impact = data?.testingImpact

  if (!impact)
    return (
      <Card title="Testing Impact">
        <p className="text-slate-400 text-sm">
          No testing impact data
        </p>
      </Card>
    )

  return (

    <Card title="Testing Impact">

      <div className="space-y-3 text-sm">

        <div className="flex justify-between">
          <span className="text-slate-400">QA Hours Lost</span>
          <span>{impact.qaHoursLost}</span>
        </div>

        <div className="flex justify-between">
          <span className="text-slate-400">Tests Failed</span>
          <span>{impact.totalTestsFailed}</span>
        </div>

        <div className="flex justify-between">
          <span className="text-slate-400">Cost Impact</span>
          <span>${impact.estimatedCostImpact}</span>
        </div>

        <div className="flex justify-between">
          <span className="text-slate-400">Engineers Impacted</span>
          <span>{impact.totalQAEngineersImpacted}</span>
        </div>

      </div>

    </Card>
  )
}

export default QAImpact