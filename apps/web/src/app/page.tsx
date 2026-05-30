import {
  Activity,
  AlertTriangle,
  ArrowRight,
  BadgeCheck,
  Database,
  FileText,
  Gavel,
  LineChart,
  Network,
  ShieldCheck,
} from "lucide-react";

const metrics = [
  {
    label: "Estimated exposure",
    value: "KES 482.4M",
    change: "31 high-confidence signals",
    tone: "text-exposure",
  },
  {
    label: "Open compliance cases",
    value: "128",
    change: "24 awaiting assignment",
    tone: "text-authority",
  },
  {
    label: "Data quality pass rate",
    value: "97.6%",
    change: "5 active source feeds",
    tone: "text-assurance",
  },
  {
    label: "Settlement variance",
    value: "KES 14.8M",
    change: "9 channels under review",
    tone: "text-revenue",
  },
];

const riskQueue = [
  {
    taxpayer: "Amani Wholesale Traders",
    signal: "VAT output mismatch",
    exposure: "KES 38.2M",
    confidence: "94%",
    severity: "High",
  },
  {
    taxpayer: "Rift Valley Logistics Ltd",
    signal: "Import-to-sales mismatch",
    exposure: "KES 27.5M",
    confidence: "89%",
    severity: "High",
  },
  {
    taxpayer: "Kisumu County Channel 4",
    signal: "Payment settlement mismatch",
    exposure: "KES 8.1M",
    confidence: "92%",
    severity: "Medium",
  },
];

const services = [
  {
    name: "Backend API",
    status: "Ready",
    description: "Spring Boot health and OpenAPI endpoints are configured.",
    icon: ShieldCheck,
  },
  {
    name: "PostgreSQL",
    status: "Compose",
    description: "Flyway-backed application database for MVP data.",
    icon: Database,
  },
  {
    name: "Analytics",
    status: "Ready",
    description: "FastAPI health endpoint prepared for model services.",
    icon: LineChart,
  },
  {
    name: "Redis",
    status: "Compose",
    description: "Local cache and future queue foundation.",
    icon: Network,
  },
];

export default function Home() {
  return (
    <main className="min-h-dvh">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-5 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <div>
            <p className="text-sm font-semibold uppercase text-authority">Revenue Intelligence</p>
            <h1 className="mt-1 text-2xl font-semibold leading-tight text-ink sm:text-3xl">
              Compliance Assurance Operations
            </h1>
          </div>
          <nav aria-label="Primary navigation" className="flex flex-wrap gap-2">
            {["Overview", "Risk Queue", "Cases", "Ingestion"].map((item) => (
              <a
                className="min-h-11 rounded-md border border-line bg-white px-4 py-2 text-sm font-medium text-ink transition hover:border-authority hover:text-authority"
                href={`#${item.toLowerCase().replace(" ", "-")}`}
                key={item}
              >
                {item}
              </a>
            ))}
          </nav>
        </div>
      </header>

      <div className="mx-auto grid w-full max-w-7xl gap-6 px-4 py-6 sm:px-6 lg:grid-cols-[1fr_360px] lg:px-8">
        <section aria-labelledby="overview" className="space-y-6">
          <div className="rounded-md border border-line bg-white p-5 shadow-panel">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <h2 id="overview" className="text-xl font-semibold text-ink">
                  Operational Snapshot
                </h2>
                <p className="mt-2 max-w-3xl text-sm leading-6 text-gray-700">
                  Synthetic demo telemetry for the local MVP foundation: ingestion readiness,
                  deterministic signals, cases, and evidence preparation.
                </p>
              </div>
              <a
                href="http://localhost:8080/api/docs"
                className="inline-flex min-h-11 items-center gap-2 rounded-md bg-authority px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#1d4a40]"
              >
                Open API Docs
                <ArrowRight size={18} aria-hidden="true" />
              </a>
            </div>

            <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {metrics.map((metric) => (
                <article className="rounded-md border border-line bg-paper p-4" key={metric.label}>
                  <p className="text-sm font-medium text-gray-700">{metric.label}</p>
                  <p className={`mt-3 text-2xl font-semibold ${metric.tone}`}>{metric.value}</p>
                  <p className="mt-2 text-sm text-gray-600">{metric.change}</p>
                </article>
              ))}
            </div>
          </div>

          <section
            id="risk-queue"
            aria-labelledby="risk-queue-heading"
            className="rounded-md border border-line bg-white shadow-panel"
          >
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <div className="flex items-center gap-3">
                <AlertTriangle className="text-exposure" size={22} aria-hidden="true" />
                <h2 id="risk-queue-heading" className="text-lg font-semibold">
                  Priority Risk Queue
                </h2>
              </div>
              <span className="rounded-md bg-[#f5e8dd] px-3 py-1 text-sm font-medium text-revenue">
                Phase 1 sample
              </span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] border-collapse text-left text-sm">
                <thead className="bg-paper text-gray-700">
                  <tr>
                    <th className="px-5 py-3 font-semibold">Taxpayer</th>
                    <th className="px-5 py-3 font-semibold">Signal</th>
                    <th className="px-5 py-3 font-semibold">Exposure</th>
                    <th className="px-5 py-3 font-semibold">Confidence</th>
                    <th className="px-5 py-3 font-semibold">Severity</th>
                  </tr>
                </thead>
                <tbody>
                  {riskQueue.map((row) => (
                    <tr className="border-t border-line" key={row.taxpayer}>
                      <td className="px-5 py-4 font-medium text-ink">{row.taxpayer}</td>
                      <td className="px-5 py-4 text-gray-700">{row.signal}</td>
                      <td className="px-5 py-4 font-semibold text-exposure">{row.exposure}</td>
                      <td className="px-5 py-4 text-gray-700">{row.confidence}</td>
                      <td className="px-5 py-4">
                        <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-ink">
                          {row.severity}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </section>

        <aside className="space-y-6" aria-label="Service readiness">
          <section className="rounded-md border border-line bg-white p-5 shadow-panel">
            <div className="flex items-center gap-3">
              <Activity className="text-authority" size={22} aria-hidden="true" />
              <h2 className="text-lg font-semibold">Phase 1 Services</h2>
            </div>
            <div className="mt-4 space-y-3">
              {services.map((service) => {
                const Icon = service.icon;

                return (
                  <article
                    className="rounded-md border border-line bg-paper p-4"
                    key={service.name}
                  >
                    <div className="flex items-start gap-3">
                      <Icon className="mt-0.5 text-authority" size={20} aria-hidden="true" />
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="font-semibold text-ink">{service.name}</h3>
                          <span className="rounded-md bg-white px-2 py-0.5 text-xs font-semibold text-assurance">
                            {service.status}
                          </span>
                        </div>
                        <p className="mt-1 text-sm leading-5 text-gray-700">
                          {service.description}
                        </p>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>

          <section
            id="cases"
            aria-labelledby="case-readiness-heading"
            className="rounded-md border border-line bg-white p-5 shadow-panel"
          >
            <div className="flex items-center gap-3">
              <Gavel className="text-revenue" size={22} aria-hidden="true" />
              <h2 id="case-readiness-heading" className="text-lg font-semibold">
                Workflow Baseline
              </h2>
            </div>
            <ul className="mt-4 space-y-3 text-sm leading-6 text-gray-700">
              <li className="flex gap-3">
                <BadgeCheck className="mt-1 shrink-0 text-assurance" size={18} aria-hidden="true" />
                Health endpoints for API and analytics are testable.
              </li>
              <li className="flex gap-3">
                <FileText className="mt-1 shrink-0 text-assurance" size={18} aria-hidden="true" />
                OpenAPI JSON and Swagger UI are available for API-first work.
              </li>
              <li className="flex gap-3">
                <Database className="mt-1 shrink-0 text-assurance" size={18} aria-hidden="true" />
                PostgreSQL and Redis can be started with Docker Compose.
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </main>
  );
}
