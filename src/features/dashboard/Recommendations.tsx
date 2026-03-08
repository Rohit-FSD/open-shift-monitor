import Card from "../../components/common/Card"

interface Props {
  data: any
}

const Recommendations = ({ data }: Props) => {

  const recommendations = data?.recommendations || []

  return (

    <Card title="Recommendations">

      {recommendations.length === 0 && (

        <p className="text-slate-400 text-sm">
          No recommendations available
        </p>

      )}

      <div className="space-y-3">

        {recommendations.map((rec: string, index: number) => (

          <div
            key={index}
            className="bg-slate-800 p-3 rounded border border-slate-700 text-sm"
          >

            ⚠ {rec}

          </div>

        ))}

      </div>

    </Card>
  )
}

export default Recommendations