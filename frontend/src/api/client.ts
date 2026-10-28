import axios from 'axios'

// All calls go to /api - nginx (Docker) or the Vite dev proxy forwards them to the backend.
export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

export function errorMessage(err: any): string {
  if (err && err.response && err.response.data && err.response.data.message) {
    return err.response.data.message
  }
  if (err && err.message) {
    return err.message
  }
  return 'Something went wrong'
}
