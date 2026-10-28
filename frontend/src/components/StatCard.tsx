export default function StatCard({ label, value, testId }: { label: string; value: number; testId?: string }) {
  return (
    <div className="stat-card">
      <div className="stat-label">{label}</div>
      <div className="stat-value" data-testid={testId}>
        {value}
      </div>
    </div>
  )
}
