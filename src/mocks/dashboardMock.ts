export const servicesMock = [
    {
      name: "css",
      image: "css:9.57.2",
      replicas: 1,
      readyReplicas: 1,
      status: "HEALTHY",
      namespace: "22429",
      pods: [
        {
          name: "css-68f9d8d6fb",
          node: "compute-4.npt.apaas4",
          restarts: 0,
          containers: [
            {
              name: "css-ui",
              image: "css-ui:9.39.18"
            }
          ]
        }
      ]
    },
    {
      name: "bcp-idv",
      image: "idv:1.2.3",
      replicas: 1,
      readyReplicas: 1,
      status: "CRITICAL",
      namespace: "22429",
      pods: []
    }
  ]
  
  export const slaMock = {
    overallSummary: {
      averageUptime: 3.64,
      totalDowntimeFormatted: "289 hrs",
      totalIncidents: 0,
      status: "CRITICAL"
    }
  }
  
  export const logFailureMock = {
    environment: "V4-QA01",
    services: [
      {
        failures: [
          {
            endpoint: "Unknown endpoint",
            httpMethod: "POST",
            statusCode: "500",
            errorType: "INTERNAL_SERVER_ERROR",
            errorMessage: "Read timed out for fraudcheck api"
          }
        ]
      }
    ]
  }