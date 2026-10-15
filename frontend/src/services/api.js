
import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL?.replace(/\/+$/, '')

const client = axios.create({
  baseURL,
  timeout: 120000,
  headers: { Accept: 'application/json' },
})

export async function analyzeRepository(repositoryUrl) {
  if (!baseURL) {
    throw new Error(
      'RepoRadar is not configured with an API URL. Set VITE_API_BASE_URL and reload the app.',
    )
  }

  try {
    const response = await client.get('/api/analyze', {
      params: { repositoryUrl },
    })

    if (!response.data.success) {
      throw new Error(response.data.message)
    }

    return response.data.data
  } catch (error) {
    throw new Error(
      error.response?.data?.message ||
        error.message ||
        'Unable to reach RepoRadar. Please try again.',
    )
  }
}

export async function generateAiInsights(repositoryUrl) {
  if (!baseURL) {
    throw new Error(
      'RepoRadar is not configured with an API URL. Set VITE_API_BASE_URL and reload the app.',
    )
  }

  try {
    const response = await client.post('/api/ai-insights', null, {
      params: { repositoryUrl },
    })

    if (!response.data.success) {
      throw new Error(response.data.message || 'AI insights generation failed.')
    }

    return response.data.data
  } catch (error) {
    throw new Error(
      error.response?.data?.message ||
        error.message ||
        'Unable to generate AI insights. Please try again.',
    )
  }
}
