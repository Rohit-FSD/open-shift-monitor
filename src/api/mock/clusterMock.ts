export const clusterMock = {
    averageUptime: 3.64,
    totalDowntimeFormatted: "289 hrs",
    totalIncidents: 0,
    status: "Critical",
  
    dailyBreakdown: [
      { day: "Mon", downtime: 600 },
      { day: "Tue", downtime: 600 },
      { day: "Wed", downtime: 600 },
      { day: "Thu", downtime: 600 },
      { day: "Fri", downtime: 568 }
    ],
  
    testingImpact: {
      totalQAHoursLost: 288,
      totalTestsFailed: 0,
      totalQAEngineersImpacted: 5,
      estimatedCostImpact: 14000
    },

    recommendations: [
        "Investigate css stability issues (5.47% uptime)",
        "Reduce unplanned downtime in css (47 hours 16 minutes this week)",
        "Investigate bcp-idv-service stability issues (5.47% uptime)",
        "Investigate css-odstest stability issues (0% uptime)"
      ]
  }