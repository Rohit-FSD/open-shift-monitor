import Card from "../../components/common/Card"
import Loader from "../../components/common/Loader"

interface Props {
  data: any
}

const SLAMetrics = ({ data }: Props) => {

  if (!data) {
    return (
      <Card title="Downtime This Week">
        <Loader text="Loading downtime metrics..." />
      </Card>
    )
  }

  const breakdown = data?.dailyBreakdown || {}

  const days = Object.keys(breakdown)
  const values = Object.values(breakdown)

  if (!days.length)
    return (
      <Card title="Downtime This Week">
        <p className="text-slate-400 text-sm">
          No downtime data available
        </p>
      </Card>
    )

  const maxValue = Math.max(...values as number[])

  return (

    <Card title="Downtime This Week">

      <div className="relative">

        {/* baseline grid */}
        <div className="absolute bottom-6 left-0 right-0 border-t border-slate-700"></div>

        <div className="flex items-end justify-between h-44 px-2">

          {values.map((value: any, index: number) => {

            const heightPercent =
              maxValue === 0 ? 0 : (value / maxValue) * 100

            return (

              <div
                key={index}
                className="flex flex-col items-center group relative"
              >

                {/* tooltip */}
                <div className="absolute -top-6 opacity-0 group-hover:opacity-100 transition text-xs bg-slate-800 px-2 py-1 rounded border border-slate-700">

                  {value} min

                </div>

                {/* bar */}
                <div className="relative flex items-end h-36">

                  <div
                    className="bg-yellow-400 rounded-md w-6 transition-all duration-500 ease-out group-hover:bg-yellow-300"
                    style={{
                      height: `${heightPercent}%`
                    }}
                  />

                </div>

                {/* label */}
                <p className="text-xs mt-2 text-slate-400">

                  {days[index].slice(0,3)}

                </p>

              </div>

            )

          })}

        </div>

      </div>

    </Card>
  )
}

export default SLAMetrics